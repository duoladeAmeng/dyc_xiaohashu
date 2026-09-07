package com.dyc.xiaohashu.id.generator.core.segment;

/**
 * Schedules background prefetch work for segment-chain generators.
 */
public interface SegmentPrefetchScheduler extends AutoCloseable {

    /**
     * Registers a recurring prefetch job.
     *
     * @param jobId unique job ID
     * @param job prefetch job
     */
    void register(String jobId, Runnable job);

    /**
     * Wakes up a registered job because ID request threads reached hunger.
     *
     * @param jobId unique job ID
     */
    void wakeup(String jobId);

    /**
     * Unregisters a prefetch job.
     *
     * @param jobId unique job ID
     */
    void unregister(String jobId);

    @Override
    void close();
}
