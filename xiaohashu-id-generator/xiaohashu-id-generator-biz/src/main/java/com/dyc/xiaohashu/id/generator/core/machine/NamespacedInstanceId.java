package com.dyc.xiaohashu.id.generator.core.machine;

import java.util.Objects;

public class NamespacedInstanceId {

    private final String namespace;
    private final InstanceId instanceId;

    public NamespacedInstanceId(String namespace, InstanceId instanceId) {
        this.namespace = namespace;
        this.instanceId = instanceId;
    }

    public String getNamespace() {
        return namespace;
    }

    public InstanceId getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NamespacedInstanceId that)) {
            return false;
        }
        return Objects.equals(namespace, that.namespace) && Objects.equals(instanceId, that.instanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, instanceId);
    }

    @Override
    public String toString() {
        return "NamespacedInstanceId{namespace='" + namespace + "', instanceId=" + instanceId + '}';
    }
}
