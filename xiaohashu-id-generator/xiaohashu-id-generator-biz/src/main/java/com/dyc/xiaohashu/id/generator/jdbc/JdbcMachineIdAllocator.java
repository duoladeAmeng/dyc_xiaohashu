package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;
import com.dyc.xiaohashu.id.generator.core.machine.AbstractMachineIdAllocator;
import com.dyc.xiaohashu.id.generator.core.machine.ClockBackwardsSynchronizer;
import com.dyc.xiaohashu.id.generator.core.machine.InMemoryMachineStateStorage;
import com.dyc.xiaohashu.id.generator.core.machine.InstanceId;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdLostException;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdAllocator;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdOverflowException;
import com.dyc.xiaohashu.id.generator.core.machine.MachineState;
import com.dyc.xiaohashu.id.generator.core.machine.MachineStateStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;

public class JdbcMachineIdAllocator extends AbstractMachineIdAllocator {

    private static final Logger log = LoggerFactory.getLogger(JdbcMachineIdAllocator.class);

    public static final String GET_MACHINE_STATE = "select machine_id, last_timestamp from cosid_machine where namespace=? and instance_id=? and last_timestamp>?";
    public static final String GET_REVERT_MACHINE_STATE = "select machine_id, last_timestamp from cosid_machine where namespace=? and (instance_id='' or last_timestamp<=?)";
    public static final String DISTRIBUTE_REVERT_MACHINE_STATE = "update cosid_machine set instance_id=?,last_timestamp=?,distribute_time=? where name=? and (instance_id='' or last_timestamp<=?)";
    public static final String NEXT_MACHINE_ID = "select max(machine_id)+1 as next_machine_id from cosid_machine where namespace=?";
    public static final String DISTRIBUTE_MACHINE = "insert into cosid_machine (name, namespace, machine_id, last_timestamp, instance_id, distribute_time, revert_time) values (?,?,?,?,?,?,0);";
    public static final String REVERT_MACHINE_STATE = "update cosid_machine set instance_id=?,last_timestamp=?,revert_time=? where namespace=? and instance_id=?";
    public static final String GUARD_MACHINE_STATE = "update cosid_machine set last_timestamp=? where namespace=? and instance_id=? and machine_id=?";

    private final DataSource dataSource;

    public JdbcMachineIdAllocator(DataSource dataSource) {
        this(dataSource, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT);
    }

    public JdbcMachineIdAllocator(DataSource dataSource, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.dataSource = dataSource;
    }

    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        try (Connection connection = dataSource.getConnection()) {
            MachineState selfMachineState = distributeBySelf(connection, namespace, instanceId, safeGuardDuration);
            if (!MachineState.NOT_FOUND.equals(selfMachineState)) {
                return selfMachineState;
            }
            MachineState revertMachineState = distributeByRevert(connection, namespace, instanceId, safeGuardDuration);
            if (!MachineState.NOT_FOUND.equals(revertMachineState)) {
                return revertMachineState;
            }
            return distributeMachine(connection, namespace, machineBit, instanceId);
        } catch (SQLException sqlException) {
            log.error("Distribute MachineId Failed.", sqlException);
            throw new IdGeneratorException(sqlException);
        }
    }

    public MachineState distributeBySelf(Connection connection, String namespace, InstanceId instanceId, Duration safeGuardDuration) throws SQLException {
        long safeGuardAt = MachineIdAllocator.getSafeGuardAt(safeGuardDuration, instanceId.isStable());
        try (PreparedStatement statement = connection.prepareStatement(GET_MACHINE_STATE)) {
            statement.setString(1, namespace);
            statement.setString(2, instanceId.getInstanceId());
            statement.setLong(3, safeGuardAt);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    MachineState machineState = MachineState.of(resultSet.getInt(1), Math.max(resultSet.getLong(2), System.currentTimeMillis()));
                    guardRemote(namespace, instanceId, machineState, safeGuardDuration);
                    return machineState;
                }
            }
        }
        return MachineState.NOT_FOUND;
    }

    public MachineState distributeByRevert(Connection connection, String namespace, InstanceId instanceId, Duration safeGuardDuration) throws SQLException {
        long safeGuardAt = MachineIdAllocator.getSafeGuardAt(safeGuardDuration, instanceId.isStable());
        try (PreparedStatement statement = connection.prepareStatement(GET_REVERT_MACHINE_STATE)) {
            statement.setString(1, namespace);
            statement.setLong(2, safeGuardAt);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    MachineState machineState = MachineState.of(resultSet.getInt(1), resultSet.getLong(2));
                    int row = distributeRevertMachineState(connection, namespace, instanceId, machineState, safeGuardAt);
                    if (row > 0) {
                        return machineState;
                    }
                }
            }
        }
        return MachineState.NOT_FOUND;
    }

    public int distributeRevertMachineState(Connection connection, String namespace, InstanceId instanceId, MachineState machineState, long safeGuardAt) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(DISTRIBUTE_REVERT_MACHINE_STATE)) {
            statement.setString(1, instanceId.getInstanceId());
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.setString(4, MachineIdAllocator.namespacedMachineId(namespace, machineState.getMachineId()));
            statement.setLong(5, safeGuardAt);
            return statement.executeUpdate();
        }
    }

    public MachineState distributeMachine(Connection connection, String namespace, int machineBit, InstanceId instanceId) throws SQLException {
        int nextMachineId = nextMachineId(connection, namespace);
        if (nextMachineId > MachineIdAllocator.maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(MachineIdAllocator.totalMachineIds(machineBit), instanceId);
        }
        MachineState machineState = MachineState.of(nextMachineId, System.currentTimeMillis());
        try {
            distributeMachine(connection, namespace, instanceId, machineState);
        } catch (SQLIntegrityConstraintViolationException sqlIntegrityConstraintViolationException) {
            log.info("MachineId Duplicate.", sqlIntegrityConstraintViolationException);
            return distributeMachine(connection, namespace, machineBit, instanceId);
        }
        return machineState;
    }

    public static int nextMachineId(Connection connection, String namespace) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(NEXT_MACHINE_ID)) {
            statement.setString(1, namespace);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
                return 0;
            }
        }
    }

    public int distributeMachine(Connection connection, String namespace, InstanceId instanceId, MachineState machineState) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DISTRIBUTE_MACHINE)) {
            statement.setString(1, MachineIdAllocator.namespacedMachineId(namespace, machineState.getMachineId()));
            statement.setString(2, namespace);
            statement.setInt(3, machineState.getMachineId());
            statement.setLong(4, machineState.getLastTimeStamp());
            statement.setString(5, instanceId.getInstanceId());
            statement.setLong(6, machineState.getLastTimeStamp());
            statement.executeUpdate();
            return statement.getUpdateCount();
        }
    }

    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(REVERT_MACHINE_STATE)) {
            statement.setString(1, instanceId.isStable() ? instanceId.getInstanceId() : "");
            statement.setLong(2, machineState.getLastTimeStamp());
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, namespace);
            statement.setString(5, instanceId.getInstanceId());
            statement.executeUpdate();
        } catch (SQLException sqlException) {
            log.error("Revert MachineId Failed.", sqlException);
            throw new IdGeneratorException(sqlException);
        }
    }

    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(GUARD_MACHINE_STATE)) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, namespace);
            statement.setString(3, instanceId.getInstanceId());
            statement.setInt(4, machineState.getMachineId());
            if (statement.executeUpdate() == 0) {
                throw new MachineIdLostException(namespace, instanceId, machineState);
            }
        } catch (SQLException sqlException) {
            log.error("Guard MachineId Failed.", sqlException);
            throw new IdGeneratorException(sqlException);
        }
    }
}
