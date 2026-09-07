package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;
import com.dyc.xiaohashu.id.generator.core.segment.IdSegment;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentAllocator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentOverflowException;
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
 * JDBC-backed segment allocator using row locks and optimistic versions.
 */
public class JdbcSegmentAllocator implements SegmentAllocator {

    private static final String SELECT_SEGMENT_FOR_UPDATE = """
            select namespace, tag, max_id, step, version
            from id_segment
            where namespace = ? and tag = ?
            for update
            """;

    private static final String INSERT_SEGMENT = """
            insert into id_segment (
                namespace, tag, max_id, step, version, last_fetch_at, updated_at, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_SEGMENT = """
            update id_segment
            set max_id = ?, step = ?, version = version + 1, last_fetch_at = ?, updated_at = ?
            where namespace = ? and tag = ? and version = ?
            """;

    private final TimeService timeService;
    private final JdbcRetryTemplate retryTemplate;
    private final JdbcTransaction transaction;

    public JdbcSegmentAllocator(DataSource dataSource) {
        this(dataSource, SystemTimeService.INSTANCE, new JdbcRetryTemplate(JdbcRetryConfig.defaults()));
    }

    public JdbcSegmentAllocator(DataSource dataSource, TimeService timeService, JdbcRetryTemplate retryTemplate) {
        this.timeService = Objects.requireNonNull(timeService, "timeService must not be null");
        this.retryTemplate = Objects.requireNonNull(retryTemplate, "retryTemplate must not be null");
        this.transaction = new JdbcTransaction(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public IdSegment nextSegment(String namespace, String tag, long requestedStep) {
        requireText(namespace, "namespace");
        requireText(tag, "tag");
        if (requestedStep <= 0) {
            throw new InvalidIdGeneratorConfigurationException("requestedStep must be greater than zero");
        }
        return retryTemplate.execute("allocate ID segment", () -> transaction.execute(connection -> allocate(connection, namespace, tag, requestedStep)));
    }

    private IdSegment allocate(Connection connection, String namespace, String tag, long requestedStep) throws SQLException {
        SegmentRow current = selectCurrent(connection, namespace, tag);
        Instant now = timeService.now();
        if (current == null) {
            insertFirstSegment(connection, namespace, tag, requestedStep, now);
            return new IdSegment(namespace, tag, 1, requestedStep);
        }
        long previousMaxId = current.maxId();
        long nextMaxId = addExact(previousMaxId, requestedStep);
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_SEGMENT)) {
            statement.setLong(1, nextMaxId);
            statement.setLong(2, requestedStep);
            statement.setTimestamp(3, timestamp(now));
            statement.setTimestamp(4, timestamp(now));
            statement.setString(5, namespace);
            statement.setString(6, tag);
            statement.setLong(7, current.version());
            int updatedRows = statement.executeUpdate();
            if (updatedRows != 1) {
                throw new SQLTransactionRollbackException("segment row was changed concurrently");
            }
        }
        return new IdSegment(namespace, tag, previousMaxId + 1, nextMaxId);
    }

    private SegmentRow selectCurrent(Connection connection, String namespace, String tag) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SEGMENT_FOR_UPDATE)) {
            statement.setString(1, namespace);
            statement.setString(2, tag);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new SegmentRow(resultSet.getLong("max_id"), resultSet.getLong("version"));
            }
        }
    }

    private void insertFirstSegment(Connection connection, String namespace, String tag, long requestedStep, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SEGMENT)) {
            statement.setString(1, namespace);
            statement.setString(2, tag);
            statement.setLong(3, requestedStep);
            statement.setLong(4, requestedStep);
            statement.setLong(5, 0L);
            statement.setTimestamp(6, timestamp(now));
            statement.setTimestamp(7, timestamp(now));
            statement.setTimestamp(8, timestamp(now));
            statement.executeUpdate();
        }
    }

    private long addExact(long previousMaxId, long requestedStep) {
        if (Long.MAX_VALUE - previousMaxId < requestedStep) {
            throw new SegmentOverflowException("segment max_id overflow");
        }
        return previousMaxId + requestedStep;
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new InvalidIdGeneratorConfigurationException(name + " must not be blank");
        }
    }

    private record SegmentRow(long maxId, long version) {
    }
}
