package com.dyc.xiaohashu.id.generator.core.machine;

import java.util.Objects;

/**
 * Migrated from CosId's InstanceId.
 */
public final class InstanceId {

    public static final InstanceId NONE = new InstanceId("none", false);

    private final String instanceId;
    private final boolean stable;

    public InstanceId(String instanceId, boolean stable) {
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

    public static InstanceId of(String host, int port, boolean stable) {
        return of(String.format("%s:%s", host, port), stable);
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
