package com.dyc.xiaohashu.id.generator.core.machine;

import java.time.Duration;

public class GuardDistribute implements MachineIdDistribute {

    private final MachineIdAllocator machineIdAllocator;
    private final MachineIdGuarder machineIdGuarder;

    public GuardDistribute(MachineIdAllocator machineIdAllocator, MachineIdGuarder machineIdGuarder) {
        this.machineIdAllocator = machineIdAllocator;
        this.machineIdGuarder = machineIdGuarder;
    }

    @Override
    public MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) throws MachineIdOverflowException {
        MachineState machineState = machineIdAllocator.distribute(namespace, machineBit, instanceId, safeGuardDuration);
        machineIdGuarder.register(namespace, instanceId);
        return machineState;
    }
}
