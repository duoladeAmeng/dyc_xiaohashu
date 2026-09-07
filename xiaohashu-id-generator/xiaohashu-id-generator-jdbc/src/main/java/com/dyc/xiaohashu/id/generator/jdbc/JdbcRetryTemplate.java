package com.dyc.xiaohashu.id.generator.jdbc;

import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

/**
 * Small bounded retry helper for allocator SQL conflicts.
 */
public class JdbcRetryTemplate {

    @FunctionalInterface
    public interface SqlSupplier<T> {

        T get() throws SQLException;
    }

    private static final long NANOS_PER_MILLIS = 1_000_000L;

    private final JdbcRetryConfig config;

    public JdbcRetryTemplate(JdbcRetryConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public <T> T execute(String operation, SqlSupplier<T> supplier) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        SQLException lastException = null;
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            try {
                return supplier.get();
            } catch (SQLException exception) {
                lastException = exception;
                if (attempt == config.maxAttempts() || !SqlExceptionClassifier.isRetryable(exception)) {
                    throw new JdbcIdGeneratorException(operation + " failed", exception);
                }
                backoff(attempt);
            }
        }
        throw new JdbcIdGeneratorException(operation + " failed", lastException);
    }

    private void backoff(int attempt) {
        long baseMillis = config.initialBackoff().toMillis();
        if (baseMillis <= 0) {
            Thread.yield();
            return;
        }
        long cappedMultiplier = Math.min(1L << Math.min(attempt - 1, 10), 1024L);
        long jitterMillis = ThreadLocalRandom.current().nextLong(baseMillis + 1);
        LockSupport.parkNanos((baseMillis * cappedMultiplier + jitterMillis) * NANOS_PER_MILLIS);
    }
}
