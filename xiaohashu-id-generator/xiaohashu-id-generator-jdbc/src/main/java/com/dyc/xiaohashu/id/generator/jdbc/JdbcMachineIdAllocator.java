package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;
import com.dyc.xiaohashu.id.generator.core.machine.InstanceIdentity;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdAllocator;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdOverflowException;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLeaseConfig;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLeaseLostException;
import com.dyc.xiaohashu.id.generator.core.machine.MachineState;
import com.dyc.xiaohashu.id.generator.core.machine.MachineStatus;
import com.dyc.xiaohashu.id.generator.core.time.SystemTimeService;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/**
 * JDBC-backed machine ID allocator using leases and optimistic versions.
 */
public class JdbcMachineIdAllocator implements MachineIdAllocator {

    private static final String SELECT_SELF_FOR_UPDATE = """
            select namespace, machine_id, instance_id, stable, status, last_timestamp, last_heartbeat_at,
                   lease_expires_at, version
            from id_machine
            where namespace = ? and instance_id = ?
            for update
            """;

    private static final String SELECT_RECLAIMABLE_FOR_UPDATE = """
            select namespace, machine_id, instance_id, stable, status, last_timestamp, last_heartbeat_at,
                   lease_expires_at, version
            from id_machine
            where namespace = ?
              and stable = false
              and (status <> ? or lease_expires_at <= ?)
            order by machine_id
            limit 1
            for update
            """;

    private static final String INSERT_MACHINE_SEQUENCE = """
            insert into id_machine_sequence (namespace, next_machine_id, created_at, updated_at)
            values (?, ?, ?, ?)
            """;

    private static final String SELECT_MACHINE_SEQUENCE_EXISTS = """
            select namespace
            from id_machine_sequence
            where namespace = ?
            """;

    private static final String SELECT_MACHINE_SEQUENCE_FOR_UPDATE = """
            select next_machine_id
            from id_machine_sequence
            where namespace = ?
            for update
            """;

    private static final String UPDATE_MACHINE_SEQUENCE = """
            update id_machine_sequence
            set next_machine_id = ?, updated_at = ?
            where namespace = ?
            """;

    private static final String INSERT_MACHINE = """
            insert into id_machine (
                namespace, machine_id, instance_id, stable, status, last_timestamp,
                last_heartbeat_at, lease_expires_at, version, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String ACTIVATE_MACHINE = """
            update id_machine
            set instance_id = ?, stable = ?, status = ?, last_timestamp = ?,
                last_heartbeat_at = ?, lease_expires_at = ?, version = version + 1, updated_at = ?
            where namespace = ? and machine_id = ? and version = ?
            """;

    private static final String HEARTBEAT_MACHINE = """
            update id_machine
            set status = ?, last_timestamp = ?, last_heartbeat_at = ?, lease_expires_at = ?,
                version = version + 1, updated_at = ?
            where namespace = ? and machine_id = ? and instance_id = ? and version = ? and status = ? and lease_expires_at > ?
            """;

    private static final String RELEASE_MACHINE = """
            update id_machine
            set status = ?, last_timestamp = ?, version = version + 1, updated_at = ?
            where namespace = ? and machine_id = ? and instance_id = ? and version = ?
            """;

    private final MachineLeaseConfig leaseConfig;
    private final TimeService timeService;
    private final JdbcRetryTemplate retryTemplate;
    private final JdbcTransaction transaction;

    public JdbcMachineIdAllocator(DataSource dataSource) {
        this(dataSource, MachineLeaseConfig.defaults(), SystemTimeService.INSTANCE, new JdbcRetryTemplate(JdbcRetryConfig.defaults()));
    }

    public JdbcMachineIdAllocator(DataSource dataSource, MachineLeaseConfig leaseConfig, TimeService timeService, JdbcRetryTemplate retryTemplate) {
        this.leaseConfig = Objects.requireNonNull(leaseConfig, "leaseConfig must not be null");
        this.timeService = Objects.requireNonNull(timeService, "timeService must not be null");
        this.retryTemplate = Objects.requireNonNull(retryTemplate, "retryTemplate must not be null");
        this.transaction = new JdbcTransaction(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public MachineLease acquire(String namespace, InstanceIdentity instanceIdentity, int maxMachineId, long lastGeneratedTimestamp) {
        requireText(namespace, "namespace");
        Objects.requireNonNull(instanceIdentity, "instanceIdentity must not be null");
        requireNonNegative(maxMachineId, "maxMachineId");
        requireNonNegative(lastGeneratedTimestamp, "lastGeneratedTimestamp");
        return retryTemplate.execute("acquire machine ID", () -> transaction.execute(connection -> {
            Instant now = timeService.now();
            MachineState self = selectSelf(connection, namespace, instanceIdentity.instanceId());
            if (self != null) {
                return activate(connection, self, instanceIdentity, maxMachineId, lastGeneratedTimestamp, now);
            }
            int nextMachineId = lockMachineSequence(connection, namespace, now);
            MachineState reclaimable = selectReclaimable(connection, namespace, now);
            if (reclaimable != null) {
                return activate(connection, reclaimable, instanceIdentity, maxMachineId, lastGeneratedTimestamp, now);
            }
            return insertNew(connection, namespace, instanceIdentity, maxMachineId, lastGeneratedTimestamp, now, nextMachineId);
        }));
    }

    @Override
    public MachineLease heartbeat(MachineLease lease, long lastGeneratedTimestamp) {
        Objects.requireNonNull(lease, "lease must not be null");
        requireNonNegative(lastGeneratedTimestamp, "lastGeneratedTimestamp");
        return retryTemplate.execute("heartbeat machine lease", () -> transaction.execute(connection -> {
            Instant now = timeService.now();
            if (!lease.canGenerateAt(now)) {
                throw new MachineLeaseLostException("machine lease is not active or has expired");
            }
            long persistedTimestamp = Math.max(lease.lastTimestamp(), lastGeneratedTimestamp);
            Instant expiresAt = now.plus(leaseConfig.leaseTimeout());
            try (PreparedStatement statement = connection.prepareStatement(HEARTBEAT_MACHINE)) {
                statement.setString(1, MachineStatus.ACTIVE.name());
                statement.setLong(2, persistedTimestamp);
                statement.setTimestamp(3, timestamp(now));
                statement.setTimestamp(4, timestamp(expiresAt));
                statement.setTimestamp(5, timestamp(now));
                statement.setString(6, lease.namespace());
                statement.setInt(7, lease.machineId());
                statement.setString(8, lease.instanceIdentity().instanceId());
                statement.setLong(9, lease.version());
                statement.setString(10, MachineStatus.ACTIVE.name());
                statement.setTimestamp(11, timestamp(now));
                int updatedRows = statement.executeUpdate();
                if (updatedRows != 1) {
                    throw new MachineLeaseLostException("machine lease heartbeat was rejected");
                }
            }
            return new MachineLease(
                    lease.namespace(),
                    lease.machineId(),
                    lease.instanceIdentity(),
                    MachineStatus.ACTIVE,
                    persistedTimestamp,
                    now,
                    expiresAt,
                    lease.version() + 1
            );
        }));
    }

    @Override
    public void release(MachineLease lease) {
        Objects.requireNonNull(lease, "lease must not be null");
        retryTemplate.execute("release machine lease", () -> transaction.execute(connection -> {
            Instant now = timeService.now();
            try (PreparedStatement statement = connection.prepareStatement(RELEASE_MACHINE)) {
                statement.setString(1, MachineStatus.RELEASED.name());
                statement.setLong(2, lease.lastTimestamp());
                statement.setTimestamp(3, timestamp(now));
                statement.setString(4, lease.namespace());
                statement.setInt(5, lease.machineId());
                statement.setString(6, lease.instanceIdentity().instanceId());
                statement.setLong(7, lease.version());
                int updatedRows = statement.executeUpdate();
                if (updatedRows != 1) {
                    throw new MachineLeaseLostException("machine lease release was rejected");
                }
            }
            return null;
        }));
    }

    private MachineLease activate(
            Connection connection,
            MachineState state,
            InstanceIdentity instanceIdentity,
            int maxMachineId,
            long lastGeneratedTimestamp,
            Instant now
    ) throws SQLException {
        ensureMachineId(state.machineId(), maxMachineId);
        long persistedTimestamp = Math.max(state.lastTimestamp(), lastGeneratedTimestamp);
        Instant expiresAt = now.plus(leaseConfig.leaseTimeout());
        try (PreparedStatement statement = connection.prepareStatement(ACTIVATE_MACHINE)) {
            statement.setString(1, instanceIdentity.instanceId());
            statement.setBoolean(2, instanceIdentity.stable());
            statement.setString(3, MachineStatus.ACTIVE.name());
            statement.setLong(4, persistedTimestamp);
            statement.setTimestamp(5, timestamp(now));
            statement.setTimestamp(6, timestamp(expiresAt));
            statement.setTimestamp(7, timestamp(now));
            statement.setString(8, state.namespace());
            statement.setInt(9, state.machineId());
            statement.setLong(10, state.version());
            int updatedRows = statement.executeUpdate();
            if (updatedRows != 1) {
                throw new SQLTransactionRollbackException("machine lease was changed concurrently");
            }
        }
        return new MachineLease(
                state.namespace(),
                state.machineId(),
                instanceIdentity,
                MachineStatus.ACTIVE,
                persistedTimestamp,
                now,
                expiresAt,
                state.version() + 1
        );
    }

    private MachineLease insertNew(
            Connection connection,
            String namespace,
            InstanceIdentity instanceIdentity,
            int maxMachineId,
            long lastGeneratedTimestamp,
            Instant now,
            int machineId
    ) throws SQLException {
        ensureMachineId(machineId, maxMachineId);
        advanceMachineSequence(connection, namespace, machineId + 1, now);
        Instant expiresAt = now.plus(leaseConfig.leaseTimeout());
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MACHINE)) {
            statement.setString(1, namespace);
            statement.setInt(2, machineId);
            statement.setString(3, instanceIdentity.instanceId());
            statement.setBoolean(4, instanceIdentity.stable());
            statement.setString(5, MachineStatus.ACTIVE.name());
            statement.setLong(6, lastGeneratedTimestamp);
            statement.setTimestamp(7, timestamp(now));
            statement.setTimestamp(8, timestamp(expiresAt));
            statement.setLong(9, 0L);
            statement.setTimestamp(10, timestamp(now));
            statement.setTimestamp(11, timestamp(now));
            statement.executeUpdate();
        }
        return new MachineLease(namespace, machineId, instanceIdentity, MachineStatus.ACTIVE, lastGeneratedTimestamp, now, expiresAt, 0);
    }

    private MachineState selectSelf(Connection connection, String namespace, String instanceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SELF_FOR_UPDATE)) {
            statement.setString(1, namespace);
            statement.setString(2, instanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return readMachineState(resultSet);
            }
        }
    }

    private MachineState selectReclaimable(Connection connection, String namespace, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_RECLAIMABLE_FOR_UPDATE)) {
            statement.setString(1, namespace);
            statement.setString(2, MachineStatus.ACTIVE.name());
            statement.setTimestamp(3, timestamp(now));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return readMachineState(resultSet);
            }
        }
    }

    private int lockMachineSequence(Connection connection, String namespace, Instant now) throws SQLException {
        ensureSequenceRow(connection, namespace, now);
        try (PreparedStatement statement = connection.prepareStatement(SELECT_MACHINE_SEQUENCE_FOR_UPDATE)) {
            statement.setString(1, namespace);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLTransactionRollbackException("machine sequence row is missing");
                }
                return resultSet.getInt("next_machine_id");
            }
        }
    }

    private void advanceMachineSequence(Connection connection, String namespace, int nextMachineId, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_MACHINE_SEQUENCE)) {
            statement.setInt(1, nextMachineId);
            statement.setTimestamp(2, timestamp(now));
            statement.setString(3, namespace);
            int updatedRows = statement.executeUpdate();
            if (updatedRows != 1) {
                throw new SQLTransactionRollbackException("machine sequence row was changed concurrently");
            }
        }
    }

    private void ensureSequenceRow(Connection connection, String namespace, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_MACHINE_SEQUENCE_EXISTS)) {
            statement.setString(1, namespace);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MACHINE_SEQUENCE)) {
            statement.setString(1, namespace);
            statement.setInt(2, 0);
            statement.setTimestamp(3, timestamp(now));
            statement.setTimestamp(4, timestamp(now));
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (!isDuplicateKey(exception)) {
                throw exception;
            }
        }
    }

    private boolean isDuplicateKey(SQLException exception) {
        return "23000".equals(exception.getSQLState()) || "23505".equals(exception.getSQLState());
    }

    private MachineState readMachineState(ResultSet resultSet) throws SQLException {
        String namespace = resultSet.getString("namespace");
        String instanceId = resultSet.getString("instance_id");
        boolean stable = resultSet.getBoolean("stable");
        return new MachineState(
                namespace,
                resultSet.getInt("machine_id"),
                new InstanceIdentity(instanceId, stable),
                MachineStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("last_timestamp"),
                resultSet.getTimestamp("last_heartbeat_at").toInstant(),
                resultSet.getTimestamp("lease_expires_at").toInstant(),
                resultSet.getLong("version")
        );
    }

    private void ensureMachineId(int machineId, int maxMachineId) {
        if (machineId > maxMachineId) {
            throw new MachineIdOverflowException("machineId exceeds maxMachineId " + maxMachineId);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new InvalidIdGeneratorConfigurationException(name + " must not be negative");
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new InvalidIdGeneratorConfigurationException(name + " must not be blank");
        }
    }
}
