package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.time.Duration;
import java.util.Objects;

/**
 * Retry configuration for short JDBC conflicts.
 *
 * @param maxAttempts maximum attempts including the first try
 * @param initialBackoff initial retry backoff
 */
public record JdbcRetryConfig(int maxAttempts, Duration initialBackoff) {

    public JdbcRetryConfig {
        if (maxAttempts <= 0) {
            throw new InvalidIdGeneratorConfigurationException("maxAttempts must be greater than zero");
        }
        Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
        if (initialBackoff.isNegative()) {
            throw new InvalidIdGeneratorConfigurationException("initialBackoff must not be negative");
        }
    }

    public static JdbcRetryConfig defaults() {
        return new JdbcRetryConfig(3, Duration.ofMillis(20));
    }
}
