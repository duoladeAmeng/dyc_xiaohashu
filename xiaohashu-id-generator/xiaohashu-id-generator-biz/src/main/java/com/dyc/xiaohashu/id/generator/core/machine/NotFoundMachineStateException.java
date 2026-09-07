package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

public class NotFoundMachineStateException extends IdGeneratorException {

    private final String namespace;
    private final InstanceId instanceId;

    public NotFoundMachineStateException(String namespace, InstanceId instanceId) {
        super(String.format("Not found the MachineState of instance[%s]@[%s]!", instanceId, namespace));
        this.namespace = namespace;
        this.instanceId = instanceId;
    }

    public String getNamespace() {
        return namespace;
    }

    public InstanceId getInstanceId() {
        return instanceId;
    }
}
