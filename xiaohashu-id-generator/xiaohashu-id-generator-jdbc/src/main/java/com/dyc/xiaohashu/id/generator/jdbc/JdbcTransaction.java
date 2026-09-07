package com.dyc.xiaohashu.id.generator.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Runs JDBC work inside an explicit transaction.
 */
public class JdbcTransaction {

    @FunctionalInterface
    public interface ConnectionCallback<T> {

        T doInConnection(Connection connection) throws SQLException;
    }

    private final DataSource dataSource;

    public JdbcTransaction(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public <T> T execute(ConnectionCallback<T> callback) throws SQLException {
        Objects.requireNonNull(callback, "callback must not be null");
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = callback.doInConnection(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error exception) {
                rollback(connection);
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private void rollback(Connection connection) throws SQLException {
        connection.rollback();
    }
}
