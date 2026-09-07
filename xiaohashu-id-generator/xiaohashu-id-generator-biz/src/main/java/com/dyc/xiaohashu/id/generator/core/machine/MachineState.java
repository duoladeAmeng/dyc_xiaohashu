package com.dyc.xiaohashu.id.generator.core.machine;

import java.util.Objects;

/**
 * Copied and modified from CosId's MachineState.
 */
public final class MachineState {

    public static final MachineState NOT_FOUND = new MachineState(-1, -1);

    private final int machineId;
    private final long lastTimestamp;

    private MachineState(int machineId, long lastTimestamp) {
        this.machineId = machineId;
        this.lastTimestamp = lastTimestamp;
    }

    public int getMachineId() {
        return machineId;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }

    public MachineState withLastTimestamp(long nextLastTimestamp) {
        return of(machineId, nextLastTimestamp);
    }

    public static MachineState of(int machineId, long lastTimestamp) {
        return new MachineState(machineId, lastTimestamp);
    }

    public static MachineState of(int machineId) {
        return of(machineId, System.currentTimeMillis());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MachineState that)) {
            return false;
        }
        return machineId == that.machineId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(machineId);
    }

    @Override
    public String toString() {
        return "MachineState{machineId=" + machineId + ", lastTimestamp=" + lastTimestamp + '}';
    }
}
