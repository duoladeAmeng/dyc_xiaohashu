package com.dyc.xiaohashu.id.generator.core.machine;

/**
 * Lifecycle status of a machine ID lease.
 */
public enum MachineStatus {

    /**
     * Machine ID is held by a live instance.
     */
    ACTIVE,

    /**
     * Machine ID was gracefully released and may be reclaimed.
     */
    RELEASED,

    /**
     * Machine ID lease expired and may be reclaimed after timestamp safety checks.
     */
    EXPIRED
}
