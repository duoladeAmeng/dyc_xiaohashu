package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.time.Instant;
import java.util.Objects;

/**
 * Local lease snapshot consumed by Snowflake generators.
 *
 * @param namespace generator namespace
 * @param machineId allocated machine ID
 * @param instanceIdentity owner instance
 * @param status lease lifecycle status
 * @param lastTimestamp last Snowflake timestamp stored for this lease
 * @param leaseAcquiredAt lease acquisition time
 * @param leaseExpiresAt lease expiration time
 * @param version optimistic lock version
 */
public record MachineLease(
        String namespace,
        int machineId,
        InstanceIdentity instanceIdentity,
        MachineStatus status,
        long lastTimestamp,
        Instant leaseAcquiredAt,
        Instant leaseExpiresAt,
        long version
) {

    public MachineLease {
        requireText(namespace, "namespace");
        if (machineId < 0) {
            throw new InvalidIdGeneratorConfigurationException("machineId must not be negative");
        }
        Objects.requireNonNull(instanceIdentity, "instanceIdentity must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(leaseAcquiredAt, "leaseAcquiredAt must not be null");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt must not be null");
        if (lastTimestamp < 0) {
            throw new InvalidIdGeneratorConfigurationException("lastTimestamp must not be negative");
        }
        if (version < 0) {
            throw new InvalidIdGeneratorConfigurationException("version must not be negative");
        }
    }

    /**
     * Returns whether this lease may still generate IDs at the given instant.
     *
     * @param now current time
     * @return true when the lease is active and not expired
     */
    public boolean canGenerateAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return MachineStatus.ACTIVE.equals(status) && now.isBefore(leaseExpiresAt);
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new InvalidIdGeneratorConfigurationException(name + " must not be blank");
        }
    }
}
