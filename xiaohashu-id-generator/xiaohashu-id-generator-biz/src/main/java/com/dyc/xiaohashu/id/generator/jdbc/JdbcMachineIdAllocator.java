package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.machine.InstanceId;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdAllocator;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdLostException;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdOverflowException;
import com.dyc.xiaohashu.id.generator.core.machine.MachineState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransactionRollbackException;
import java.time.Duration;

/**
 * Copied and modified from CosId's JdbcMachineIdDistributor.
 */
public class JdbcMachineIdAllocator implements MachineIdAllocator {

    private static final Logger log = LoggerFactory.getLogger(JdbcMachineIdAllocator.class);

    private static final String GET_SELF =
            "select machine_id,last_timestamp from id_generator_machine where namespace=? and instance_id=? and last_timestamp>? order by machine_id limit 1 for update";
    private static final String GET_RECLAIM =
            "select machine_id,last_timestamp from id_generator_machine where namespace=? and (instance_id='' or status='FREE' or last_timestamp<=?) order by machine_id limit 1 for update";
    private static final String NEXT_MACHINE_ID =
            "select coalesce(max(machine_id)+1,0) from id_generator_machine where namespace=?";
    private static final String INSERT_MACHINE =
            "insert into id_generator_machine(name,namespace,machine_id,instance_id,stable_instance,last_timestamp,last_heartbeat,status,version,distribute_time,revert_time) values(?,?,?,?,?,?,current_timestamp(3),'ACTIVE',0,current_timestamp(3),null)";
    private static final String UPDATE_MACHINE =
            "update id_generator_machine set instance_id=?, stable_instance=?, last_timestamp=?, last_heartbeat=current_timestamp(3), status='ACTIVE', version=version+1, distribute_time=current_timestamp(3) where name=?";
    private static final String GUARD_MACHINE =
            "update id_generator_machine set last_timestamp=?, last_heartbeat=current_timestamp(3), status='ACTIVE', version=version+1 where namespace=? and instance_id=? and machine_id=?";
    private static final String RELEASE_STABLE =
            "update id_generator_machine set last_timestamp=?, status='FREE', version=version+1, revert_time=current_timestamp(3) where namespace=? and instance_id=? and machine_id=?";
    private static final String RELEASE_UNSTABLE =
            "update id_generator_machine set instance_id='', stable_instance=false, last_timestamp=?, status='FREE', version=version+1, revert_time=current_timestamp(3) where namespace=? and instance_id=? and machine_id=?";

    private final DataSource dataSource;
    private final JdbcRetryExecutor retryExecutor;

    public JdbcMachineIdAllocator(DataSource dataSource, JdbcRetryExecutor retryExecutor) {
        this.dataSource = dataSource;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public MachineState acquire(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        validate(namespace, instanceId, safeGuardDuration);
        return retryExecutor.execute(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    MachineState self = acquireSelf(connection, namespace, instanceId, safeGuardDuration);
                    if (self != MachineState.NOT_FOUND) {
                        connection.commit();
                        log.info("Reused machineId {} for {}@{}.", self.getMachineId(), instanceId, namespace);
                        return self;
                    }

                    MachineState reclaimed = acquireReclaimed(connection, namespace, instanceId, safeGuardDuration);
                    if (reclaimed != MachineState.NOT_FOUND) {
                        connection.commit();
                        log.info("Reclaimed machineId {} for {}@{}.", reclaimed.getMachineId(), instanceId, namespace);
                        return reclaimed;
                    }

                    MachineState created = createMachine(connection, namespace, machineBit, instanceId);
                    connection.commit();
                    log.info("Created machineId {} for {}@{}.", created.getMachineId(), instanceId, namespace);
                    return created;
                } catch (SQLIntegrityConstraintViolationException duplicate) {
                    rollbackQuietly(connection);
                    throw new SQLTransactionRollbackException("MachineId insert conflict, retry acquire.", duplicate);
                } catch (SQLException | RuntimeException exception) {
                    rollbackQuietly(connection);
                    throw exception;
                }
            }
        });
    }

    private MachineState acquireSelf(Connection connection, String namespace, InstanceId instanceId, Duration safeGuardDuration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(GET_SELF)) {
            statement.setString(1, namespace);
            statement.setString(2, instanceId.getInstanceId());
            statement.setLong(3, MachineIdAllocator.getSafeGuardAt(safeGuardDuration, instanceId.isStable()));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return MachineState.NOT_FOUND;
                }
                int machineId = resultSet.getInt(1);
                long lastTimestamp = Math.max(resultSet.getLong(2), System.currentTimeMillis());
                updateMachine(connection, namespace, machineId, instanceId, lastTimestamp);
                return MachineState.of(machineId, lastTimestamp);
            }
        }
    }

    private MachineState acquireReclaimed(Connection connection, String namespace, InstanceId instanceId, Duration safeGuardDuration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(GET_RECLAIM)) {
            statement.setString(1, namespace);
            statement.setLong(2, MachineIdAllocator.getSafeGuardAt(safeGuardDuration, instanceId.isStable()));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return MachineState.NOT_FOUND;
                }
                int machineId = resultSet.getInt(1);
                long lastTimestamp = Math.max(resultSet.getLong(2), System.currentTimeMillis());
                updateMachine(connection, namespace, machineId, instanceId, lastTimestamp);
                return MachineState.of(machineId, lastTimestamp);
            }
        }
    }

    private MachineState createMachine(Connection connection, String namespace, int machineBit, InstanceId instanceId) throws SQLException {
        int machineId = nextMachineId(connection, namespace);
        if (machineId > MachineIdAllocator.maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(MachineIdAllocator.totalMachineIds(machineBit), instanceId);
        }
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MACHINE)) {
            statement.setString(1, MachineIdAllocator.namespacedMachineId(namespace, machineId));
            statement.setString(2, namespace);
            statement.setInt(3, machineId);
            statement.setString(4, instanceId.getInstanceId());
            statement.setBoolean(5, instanceId.isStable());
            statement.setLong(6, now);
            statement.executeUpdate();
        }
        return MachineState.of(machineId, now);
    }

    private int nextMachineId(Connection connection, String namespace) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(NEXT_MACHINE_ID)) {
            statement.setString(1, namespace);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private void updateMachine(Connection connection, String namespace, int machineId, InstanceId instanceId, long lastTimestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_MACHINE)) {
            statement.setString(1, instanceId.getInstanceId());
            statement.setBoolean(2, instanceId.isStable());
            statement.setLong(3, lastTimestamp);
            statement.setString(4, MachineIdAllocator.namespacedMachineId(namespace, machineId));
            statement.executeUpdate();
        }
    }

    @Override
    public void guard(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        validate(namespace, instanceId, safeGuardDuration);
        retryExecutor.execute(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(GUARD_MACHINE)) {
                statement.setLong(1, Math.max(machineState.getLastTimestamp(), System.currentTimeMillis()));
                statement.setString(2, namespace);
                statement.setString(3, instanceId.getInstanceId());
                statement.setInt(4, machineState.getMachineId());
                int affected = statement.executeUpdate();
                if (affected == 0) {
                    throw new MachineIdLostException(namespace, instanceId, machineState);
                }
                return null;
            }
        });
    }

    @Override
    public void release(String namespace, InstanceId instanceId, MachineState machineState) {
        try {
            retryExecutor.execute(() -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement(instanceId.isStable() ? RELEASE_STABLE : RELEASE_UNSTABLE)) {
                    statement.setLong(1, Math.max(machineState.getLastTimestamp(), System.currentTimeMillis()));
                    statement.setString(2, namespace);
                    statement.setString(3, instanceId.getInstanceId());
                    statement.setInt(4, machineState.getMachineId());
                    statement.executeUpdate();
                    return null;
                }
            });
            log.info("Released machineId {} for {}@{}.", machineState.getMachineId(), instanceId, namespace);
        } catch (RuntimeException exception) {
            log.warn("Release machineId {} for {}@{} failed.", machineState.getMachineId(), instanceId, namespace, exception);
        }
    }

    private void validate(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace can not be empty.");
        }
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId can not be null.");
        }
        if (safeGuardDuration == null || safeGuardDuration.isNegative() || safeGuardDuration.isZero()) {
            throw new IllegalArgumentException("safeGuardDuration must be greater than 0.");
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            log.warn("Rollback id_generator_machine transaction failed.", ignored);
        }
    }
}
