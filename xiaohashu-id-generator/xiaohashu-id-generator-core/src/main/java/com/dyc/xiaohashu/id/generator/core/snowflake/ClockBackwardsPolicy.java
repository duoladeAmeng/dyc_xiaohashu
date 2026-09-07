package com.dyc.xiaohashu.id.generator.core.snowflake;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.time.Duration;
import java.util.Objects;

/**
 * Policy for handling local wall-clock rollback in Snowflake generation.
 *
 * @param spinThreshold rollback duration eligible for spin waiting
 * @param maxWait maximum rollback duration eligible for waiting
 */
public record ClockBackwardsPolicy(Duration spinThreshold, Duration maxWait) {

    public static final ClockBackwardsPolicy DEFAULT = new ClockBackwardsPolicy(
            Duration.ofMillis(1),
            Duration.ofMillis(500)
    );

    public ClockBackwardsPolicy {
        Objects.requireNonNull(spinThreshold, "spinThreshold must not be null");
        Objects.requireNonNull(maxWait, "maxWait must not be null");
        if (spinThreshold.isNegative()) {
            throw new InvalidIdGeneratorConfigurationException("spinThreshold must not be negative");
        }
        if (maxWait.isNegative() || maxWait.isZero()) {
            throw new InvalidIdGeneratorConfigurationException("maxWait must be greater than zero");
        }
        if (spinThreshold.compareTo(maxWait) > 0) {
            throw new InvalidIdGeneratorConfigurationException("spinThreshold must not be greater than maxWait");
        }
    }
}
