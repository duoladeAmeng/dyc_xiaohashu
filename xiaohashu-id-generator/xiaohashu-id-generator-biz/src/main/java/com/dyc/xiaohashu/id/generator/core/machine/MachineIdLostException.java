package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

public class MachineIdLostException extends IdGeneratorException {

    public MachineIdLostException(String namespace, InstanceId instanceId, MachineState machineState) {
        super("The machine id " + machineState + " bound to " + instanceId + "@" + namespace + " has been lost.");
    }
}
