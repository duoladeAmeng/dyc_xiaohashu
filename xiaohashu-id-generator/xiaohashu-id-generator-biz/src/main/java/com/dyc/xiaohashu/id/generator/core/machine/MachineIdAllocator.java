package com.dyc.xiaohashu.id.generator.core.machine;

import java.time.Duration;

/**
 * MachineId 分配抽象。当前只有 JDBC 实现，未来可新增 Redis/ZooKeeper 实现而不改 Snowflake 算法。
 */
public interface MachineIdAllocator {

    Duration FOREVER_SAFE_GUARD_DURATION = Duration.ofMillis(Long.MAX_VALUE);

    MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration);

    void revert(String namespace, InstanceId instanceId);

    void guard(String namespace, InstanceId instanceId, Duration safeGuardDuration);

    static int maxMachineId(int machineBit) {
        if (machineBit <= 0 || machineBit >= Integer.SIZE) {
            throw new IllegalArgumentException("machineBit must be between 1 and 31.");
        }
        return ~(-1 << machineBit);
    }

    static int totalMachineIds(int machineBit) {
        return maxMachineId(machineBit) + 1;
    }

    static String namespacedMachineId(String namespace, int machineId) {
        return namespace + "." + String.format("%08d", machineId);
    }

    static long getSafeGuardAt(Duration safeGuardDuration, boolean stable) {
        if (stable) {
            return 0L;
        }
        if (FOREVER_SAFE_GUARD_DURATION.equals(safeGuardDuration)) {
            return 0L;
        }
        long safeGuardAt = System.currentTimeMillis() - safeGuardDuration.toMillis();
        if (safeGuardAt < 0) {
            return 0L;
        }
        return safeGuardAt;
    }
}
