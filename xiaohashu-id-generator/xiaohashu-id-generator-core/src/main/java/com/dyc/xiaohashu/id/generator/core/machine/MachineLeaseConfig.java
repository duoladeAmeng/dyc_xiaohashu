package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.time.Duration;
import java.util.Objects;

/**
 * Lease timing configuration for machine ID allocators.
 *
 * @param heartbeatInterval expected heartbeat interval
 * @param leaseTimeout duration after which a lease may be reclaimed
 * @param maxHeartbeatFailures tolerated consecutive heartbeat failures
 */
public record MachineLeaseConfig(
        Duration heartbeatInterval,
        Duration leaseTimeout,
        int maxHeartbeatFailures
) {

    public static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
    public static final Duration DEFAULT_LEASE_TIMEOUT = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_HEARTBEAT_FAILURES = 2;

    public MachineLeaseConfig {
        Objects.requireNonNull(heartbeatInterval, "heartbeatInterval must not be null");
        Objects.requireNonNull(leaseTimeout, "leaseTimeout must not be null");
        if (heartbeatInterval.isZero() || heartbeatInterval.isNegative()) {
            throw new InvalidIdGeneratorConfigurationException("heartbeatInterval must be greater than zero");
        }
        if (leaseTimeout.isZero() || leaseTimeout.isNegative()) {
            throw new InvalidIdGeneratorConfigurationException("leaseTimeout must be greater than zero");
        }
        if (leaseTimeout.compareTo(heartbeatInterval) <= 0) {
            throw new InvalidIdGeneratorConfigurationException("leaseTimeout must be greater than heartbeatInterval");
        }
        if (maxHeartbeatFailures < 0) {
            throw new InvalidIdGeneratorConfigurationException("maxHeartbeatFailures must not be negative");
        }
    }

    /**
     * Returns the default lease timing used by the JDBC allocator.
     *
     * @return default machine lease config
     */
    public static MachineLeaseConfig defaults() {
        return new MachineLeaseConfig(DEFAULT_HEARTBEAT_INTERVAL, DEFAULT_LEASE_TIMEOUT, DEFAULT_MAX_HEARTBEAT_FAILURES);
    }
}
