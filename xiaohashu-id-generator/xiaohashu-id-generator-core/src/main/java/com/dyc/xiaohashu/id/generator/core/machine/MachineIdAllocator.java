package com.dyc.xiaohashu.id.generator.core.machine;

/**
 * Allocates and maintains unique machine IDs for Snowflake generators.
 */
public interface MachineIdAllocator {

    /**
     * Acquires a machine ID lease for one instance.
     *
     * @param namespace generator namespace
     * @param instanceIdentity owner instance
     * @param maxMachineId largest machine ID allowed by Snowflake bit layout
     * @param lastGeneratedTimestamp last local Snowflake timestamp, or zero at first startup
     * @return acquired lease
     */
    MachineLease acquire(String namespace, InstanceIdentity instanceIdentity, int maxMachineId, long lastGeneratedTimestamp);

    /**
     * Extends an existing lease and persists the latest generated timestamp.
     *
     * @param lease current lease
     * @param lastGeneratedTimestamp latest generated Snowflake timestamp
     * @return refreshed lease
     */
    MachineLease heartbeat(MachineLease lease, long lastGeneratedTimestamp);

    /**
     * Releases a lease during graceful shutdown.
     *
     * @param lease current lease
     */
    void release(MachineLease lease);
}
