package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.time.Instant;
import java.util.Objects;

/**
 * Persistent machine ID state stored by a {@link MachineIdAllocator}.
 *
 * @param namespace generator namespace
 * @param machineId allocated machine ID
 * @param instanceIdentity owner instance
 * @param status lease lifecycle status
 * @param lastTimestamp last Snowflake timestamp observed for this machine ID
 * @param lastHeartbeatAt last successful heartbeat time
 * @param leaseExpiresAt lease expiration time
 * @param version optimistic lock version
 */
public record MachineState(
        String namespace,
        int machineId,
        InstanceIdentity instanceIdentity,
        MachineStatus status,
        long lastTimestamp,
        Instant lastHeartbeatAt,
        Instant leaseExpiresAt,
        long version
) {

    public MachineState {
        requireText(namespace, "namespace");
        if (machineId < 0) {
            throw new InvalidIdGeneratorConfigurationException("machineId must not be negative");
        }
        Objects.requireNonNull(instanceIdentity, "instanceIdentity must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt must not be null");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt must not be null");
        if (lastTimestamp < 0) {
            throw new InvalidIdGeneratorConfigurationException("lastTimestamp must not be negative");
        }
        if (version < 0) {
            throw new InvalidIdGeneratorConfigurationException("version must not be negative");
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new InvalidIdGeneratorConfigurationException(name + " must not be blank");
        }
    }
}
