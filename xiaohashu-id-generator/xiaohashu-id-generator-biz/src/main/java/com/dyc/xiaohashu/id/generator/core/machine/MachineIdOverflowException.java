package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

public class MachineIdOverflowException extends IdGeneratorException {

    public MachineIdOverflowException(int totalMachineIds, InstanceId instanceId) {
        super("InstanceId " + instanceId + " distribution failed, totalMachineIds " + totalMachineIds + ".");
    }
}
