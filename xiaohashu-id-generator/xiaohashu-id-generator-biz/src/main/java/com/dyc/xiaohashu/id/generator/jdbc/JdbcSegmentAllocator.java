package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.segment.SegmentAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Copied and modified from CosId's JdbcIdSegmentDistributor.
 */
public class JdbcSegmentAllocator implements SegmentAllocator {

    private static final Logger log = LoggerFactory.getLogger(JdbcSegmentAllocator.class);

    public static final String INCREMENT_MAX_ID_SQL =
            "update id_generator_segment set last_max_id=(last_max_id + ?), last_fetch_time=current_timestamp(3), version=version + 1 where namespace=? and name=?";
    public static final String FETCH_MAX_ID_SQL =
            "select last_max_id from id_generator_segment where namespace=? and name=?";

    private final String namespace;
    private final String name;
    private final long step;
    private final DataSource dataSource;
    private final JdbcRetryExecutor retryExecutor;

    public JdbcSegmentAllocator(String namespace, String name, long step, DataSource dataSource, JdbcRetryExecutor retryExecutor) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace can not be empty.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name can not be empty.");
        }
        SegmentAllocator.ensureStep(step);
        this.namespace = namespace;
        this.name = name;
        this.step = step;
        this.dataSource = dataSource;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getStep() {
        return step;
    }

    @Override
    public long nextMaxId(long step) {
        SegmentAllocator.ensureStep(step);
        return retryExecutor.execute(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    incrementMaxId(connection, step);
                    long maxId = fetchMaxId(connection);
                    connection.commit();
                    log.info("Allocated segment {} maxId {} step {}.", getNamespacedName(), maxId, step);
                    return maxId;
                } catch (SQLException | RuntimeException exception) {
                    rollbackQuietly(connection);
                    throw exception;
                }
            }
        });
    }

    private void incrementMaxId(Connection connection, long step) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INCREMENT_MAX_ID_SQL)) {
            statement.setLong(1, step);
            statement.setString(2, namespace);
            statement.setString(3, name);
            int affected = statement.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Segment name missing: " + getNamespacedName());
            }
        }
    }

    private long fetchMaxId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FETCH_MAX_ID_SQL)) {
            statement.setString(1, namespace);
            statement.setString(2, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Not found max id: " + getNamespacedName());
                }
                return resultSet.getLong(1);
            }
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            log.warn("Rollback id_generator_segment transaction failed.", ignored);
        }
    }
}
