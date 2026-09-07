package com.dyc.xiaohashu.id.generator.core.machine;

import static com.dyc.xiaohashu.id.generator.core.machine.ClockBackwardsSynchronizer.getBackwardsTimeStamp;

import java.time.Duration;

public abstract class AbstractMachineIdAllocator implements MachineIdAllocator {

    public static final int NOT_FOUND_LAST_STAMP = -1;

    private final MachineStateStorage machineStateStorage;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;

    public AbstractMachineIdAllocator(MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this.machineStateStorage = machineStateStorage;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
    }

    @Override
    public MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) throws MachineIdOverflowException {
        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace can not be empty!");
        }
        if (machineBit <= 0) {
            throw new IllegalArgumentException(String.format("machineBit:[%s] must be greater than 0!", machineBit));
        }
        if (instanceId == null) {
            throw new NullPointerException("instanceId can not be null!");
        }

        MachineState localState = machineStateStorage.get(namespace, instanceId);
        if (!MachineState.NOT_FOUND.equals(localState)) {
            ensureMachineId(machineBit, instanceId, localState);
            clockBackwardsSynchronizer.syncUninterruptibly(localState.getLastTimeStamp());
            return localState;
        }

        localState = distributeRemote(namespace, machineBit, instanceId, safeGuardDuration);
        ensureMachineId(machineBit, instanceId, localState);
        if (ClockBackwardsSynchronizer.getBackwardsTimeStamp(localState.getLastTimeStamp()) > 0) {
            clockBackwardsSynchronizer.syncUninterruptibly(localState.getLastTimeStamp());
            localState = MachineState.of(localState.getMachineId(), System.currentTimeMillis());
        }

        machineStateStorage.set(namespace, localState.getMachineId(), instanceId);
        return localState;
    }

    protected abstract MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration);

    private void ensureMachineId(int machineBit, InstanceId instanceId, MachineState machineState) {
        if (machineState.getMachineId() > MachineIdAllocator.maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(MachineIdAllocator.totalMachineIds(machineBit), instanceId);
        }
    }

    @Override
    public void revert(String namespace, InstanceId instanceId) {
        MachineState lastLocalState = resetStorage(namespace, instanceId);
        revertRemote(namespace, instanceId, lastLocalState);
        machineStateStorage.remove(namespace, instanceId);
    }

    protected abstract void revertRemote(String namespace, InstanceId instanceId, MachineState machineState);

    @Override
    public void guard(String namespace, InstanceId instanceId, Duration safeGuardDuration) throws NotFoundMachineStateException, MachineIdLostException {
        MachineState lastLocalState = resetStorage(namespace, instanceId);
        guardRemote(namespace, instanceId, lastLocalState, safeGuardDuration);
    }

    private MachineState resetStorage(String namespace, InstanceId instanceId) {
        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace can not be empty!");
        }
        if (instanceId == null) {
            throw new NullPointerException("instanceId can not be null!");
        }

        MachineState lastLocalState = machineStateStorage.get(namespace, instanceId);
        if (MachineState.NOT_FOUND.equals(lastLocalState)) {
            throw new NotFoundMachineStateException(namespace, instanceId);
        }
        if (getBackwardsTimeStamp(lastLocalState.getLastTimeStamp()) < 0) {
            lastLocalState = MachineState.of(lastLocalState.getMachineId(), System.currentTimeMillis());
            machineStateStorage.set(namespace, lastLocalState.getMachineId(), instanceId);
        }
        return lastLocalState;
    }

    protected abstract void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration);
}
