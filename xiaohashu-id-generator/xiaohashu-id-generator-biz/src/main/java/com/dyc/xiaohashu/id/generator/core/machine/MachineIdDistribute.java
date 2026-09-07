package com.dyc.xiaohashu.id.generator.core.machine;

import java.time.Duration;

public interface MachineIdDistribute {

    MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) throws MachineIdOverflowException;
}
