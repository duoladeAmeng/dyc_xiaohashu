package com.dyc.xiaohashu.id.generator.core.machine;

import java.util.Objects;

/**
 * Copied and modified from CosId's InstanceId.
 */
public final class InstanceId {

    private final String instanceId;
    private final boolean stable;

    public InstanceId(String instanceId, boolean stable) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId can not be empty.");
        }
        this.instanceId = instanceId;
        this.stable = stable;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public boolean isStable() {
        return stable;
    }

    public static InstanceId of(String instanceId, boolean stable) {
        return new InstanceId(instanceId, stable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InstanceId that)) {
            return false;
        }
        return stable == that.stable && Objects.equals(instanceId, that.instanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId, stable);
    }

    @Override
    public String toString() {
        return "InstanceId{instanceId='" + instanceId + "', stable=" + stable + '}';
    }
}
