package com.dyc.xiaohashu.id.generator.core.machine;

import java.util.Objects;

/**
 * Migrated from CosId's MachineState.
 */
public final class MachineState {

    public static final MachineState NOT_FOUND = new MachineState(-1, -1);
    public static final String STATE_DELIMITER = "|";

    private final int machineId;
    private final long lastTimeStamp;

    public MachineState(int machineId, long lastTimeStamp) {
        this.machineId = machineId;
        this.lastTimeStamp = lastTimeStamp;
    }

    public int getMachineId() {
        return machineId;
    }

    public long getLastTimeStamp() {
        return lastTimeStamp;
    }

    public long getLastTimestamp() {
        return lastTimeStamp;
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

    public static MachineState of(String stateString) {
        String[] stateSplits = stateString.split("\\|");
        if (stateSplits.length != 2) {
            throw new IllegalArgumentException(String.format("Machine status data:[{%s}] format error.", stateString));
        }
        int machineId = Integer.parseInt(stateSplits[0]);
        long lastStamp = Long.parseLong(stateSplits[1]);
        return MachineState.of(machineId, lastStamp);
    }

    public String toStateString() {
        return machineId + STATE_DELIMITER + lastTimeStamp;
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
        return "MachineState{machineId=" + machineId + ", lastTimeStamp=" + lastTimeStamp + '}';
    }
}
