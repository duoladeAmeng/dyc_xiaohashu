package com.dyc.xiaohashu.id.generator.core.machine;

public interface MachineStateStorage {

    MachineStateStorage LOCAL = new LocalMachineStateStorage();
    MachineStateStorage IN_MEMORY = new InMemoryMachineStateStorage();

    MachineState get(String namespace, InstanceId instanceId);

    void set(String namespace, int machineId, InstanceId instanceId);

    void remove(String namespace, InstanceId instanceId);

    void clear(String namespace);

    int size(String namespace);

    boolean exists(String namespace, InstanceId instanceId);
}
