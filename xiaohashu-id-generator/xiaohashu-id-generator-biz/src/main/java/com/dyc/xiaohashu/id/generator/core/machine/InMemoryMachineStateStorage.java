package com.dyc.xiaohashu.id.generator.core.machine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMachineStateStorage implements MachineStateStorage {

    private static final Logger log = LoggerFactory.getLogger(InMemoryMachineStateStorage.class);

    private final ConcurrentHashMap<NamespacedInstanceId, MachineState> states = new ConcurrentHashMap<>();

    @Override
    public MachineState get(String namespace, InstanceId instanceId) {
        return states.getOrDefault(new NamespacedInstanceId(namespace, instanceId), MachineState.NOT_FOUND);
    }

    @Override
    public void set(String namespace, int machineId, InstanceId instanceId) {
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(namespace, instanceId);
        MachineState machineState = MachineState.of(machineId, System.currentTimeMillis());
        log.debug("Set [{}] to [{}].", namespacedInstanceId, machineState);
        states.put(namespacedInstanceId, machineState);
    }

    @Override
    public void remove(String namespace, InstanceId instanceId) {
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(namespace, instanceId);
        log.info("Remove : [{}].", namespace);
        states.remove(namespacedInstanceId);
    }

    @Override
    public void clear(String namespace) {
        log.info("Clear namespace : [{}].", namespace);
        states.keySet().removeIf(namespacedInstanceId -> namespace.equals(namespacedInstanceId.getNamespace()));
    }

    @Override
    public int size(String namespace) {
        return (int) states.keySet()
                .stream()
                .filter(namespacedInstanceId -> namespace.equals(namespacedInstanceId.getNamespace()))
                .count();
    }

    @Override
    public boolean exists(String namespace, InstanceId instanceId) {
        return states.containsKey(new NamespacedInstanceId(namespace, instanceId));
    }
}
