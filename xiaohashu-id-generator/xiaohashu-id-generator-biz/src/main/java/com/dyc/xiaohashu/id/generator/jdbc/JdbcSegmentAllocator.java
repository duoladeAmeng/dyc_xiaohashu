package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentAllocator;
import com.dyc.xiaohashu.id.generator.jdbc.exception.NotFoundMaxIdException;
import com.dyc.xiaohashu.id.generator.jdbc.exception.SegmentNameMissingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class JdbcSegmentAllocator implements SegmentAllocator {

    private static final Logger log = LoggerFactory.getLogger(JdbcSegmentAllocator.class);

    public static final String INCREMENT_MAX_ID_SQL = "update cosid set last_max_id=(last_max_id + ?),last_fetch_time=unix_timestamp() where name = ?;";
    public static final String FETCH_MAX_ID_SQL = "select last_max_id from cosid where name = ?;";

    private final String namespace;
    private final String name;
    private final long step;
    private final DataSource dataSource;

    public JdbcSegmentAllocator(String namespace, String name, long step, DataSource dataSource) {
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
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            incrementMaxId(connection, step);
            long maxId = fetchMaxId(connection);
            connection.commit();
            return maxId;
        } catch (SQLException sqlException) {
            log.error("NextMaxId failed.", sqlException);
            throw new IdGeneratorException(sqlException);
        }
    }

    private void incrementMaxId(Connection connection, long step) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INCREMENT_MAX_ID_SQL)) {
            statement.setLong(1, step);
            statement.setString(2, getNamespacedName());
            int affected = statement.executeUpdate();
            if (affected == 0) {
                throw new SegmentNameMissingException(namespace, name);
            }
        }
    }

    private long fetchMaxId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FETCH_MAX_ID_SQL)) {
            statement.setString(1, getNamespacedName());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new NotFoundMaxIdException(getNamespacedName());
                }
                return resultSet.getLong(1);
            }
        }
    }
}
