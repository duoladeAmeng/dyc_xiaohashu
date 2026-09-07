package com.dyc.xiaohashu.id.generator.core.machine;

import java.util.Map;

public interface MachineIdGuarder {

    MachineIdGuarder NONE = new None();

    Map<NamespacedInstanceId, GuardianState> getGuardianStates();

    default boolean hasFailure() {
        return getGuardianStates().values().stream().anyMatch(GuardianState::isFailed);
    }

    void register(String namespace, InstanceId instanceId);

    void unregister(String namespace, InstanceId instanceId);

    void start();

    void stop();

    boolean isRunning();

    class None implements MachineIdGuarder {

        @Override
        public Map<NamespacedInstanceId, GuardianState> getGuardianStates() {
            return Map.of();
        }

        @Override
        public void register(String namespace, InstanceId instanceId) {
        }

        @Override
        public void unregister(String namespace, InstanceId instanceId) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }
}
