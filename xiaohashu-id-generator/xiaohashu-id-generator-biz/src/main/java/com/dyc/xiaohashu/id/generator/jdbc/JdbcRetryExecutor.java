package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransactionRollbackException;
import java.sql.SQLTransientException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class JdbcRetryExecutor {

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public JdbcRetryExecutor(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be greater than 0.");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
    }

    public <T> T execute(SqlSupplier<T> supplier) {
        SQLException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (SQLException sqlException) {
                last = sqlException;
                if (!isTransient(sqlException) || attempt == maxAttempts) {
                    throw new IdGeneratorException(sqlException.getMessage(), sqlException);
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw new IdGeneratorException(last);
    }

    private void sleepBeforeRetry(int attempt) {
        long baseMillis = Math.min(initialBackoff.toMillis() * (1L << Math.min(attempt - 1, 10)), maxBackoff.toMillis());
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(baseMillis / 2, 1), Math.max(baseMillis + 1, 2));
        try {
            TimeUnit.MILLISECONDS.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdGeneratorException("Interrupted while retrying JDBC operation.", e);
        }
    }

    private boolean isTransient(SQLException exception) {
        if (exception instanceof SQLTransientException
                || exception instanceof SQLRecoverableException
                || exception instanceof SQLTimeoutException
                || exception instanceof SQLTransactionRollbackException) {
            return true;
        }
        String sqlState = exception.getSQLState();
        int errorCode = exception.getErrorCode();
        return "40001".equals(sqlState) || errorCode == 1213 || errorCode == 1205;
    }

    @FunctionalInterface
    public interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
