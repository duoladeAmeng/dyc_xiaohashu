package com.dyc.config;

import com.dyc.xiaohashu.id.generator.core.machine.GuardianState;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdAllocator;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdGuarder;
import com.dyc.xiaohashu.id.generator.core.machine.NamespacedInstanceId;
import org.springframework.context.SmartLifecycle;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class CosIdMachineIdLifecycle implements SmartLifecycle {

    private final MachineIdGuarder machineIdGuarder;
    private final MachineIdAllocator machineIdAllocator;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CosIdMachineIdLifecycle(MachineIdGuarder machineIdGuarder, MachineIdAllocator machineIdAllocator) {
        this.machineIdGuarder = machineIdGuarder;
        this.machineIdAllocator = machineIdAllocator;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            machineIdGuarder.start();
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            Map<NamespacedInstanceId, GuardianState> guardianStates = machineIdGuarder.getGuardianStates();
            machineIdGuarder.stop();
            guardianStates.forEach((registeredInstance, guardianState) ->
                    machineIdAllocator.revert(registeredInstance.getNamespace(), registeredInstance.getInstanceId()));
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
