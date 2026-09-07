package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.util.Objects;

/**
 * Identifies one running service instance.
 *
 * @param instanceId stable or ephemeral instance identifier
 * @param stable whether this identity should retain its machine ID across restarts
 */
public record InstanceIdentity(String instanceId, boolean stable) {

    public InstanceIdentity {
        Objects.requireNonNull(instanceId, "instanceId must not be null");
        if (instanceId.isBlank()) {
            throw new InvalidIdGeneratorConfigurationException("instanceId must not be blank");
        }
    }
}
