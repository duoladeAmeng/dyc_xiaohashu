package com.dyc.xiaohashu.id.generator.core.segment.concurrent;

/**
 * Copied and modified from CosId's PrefetchWorker.
 */
public interface PrefetchWorker {

    String getName();

    void submit(AffinityJob affinityJob);

    void cancel(AffinityJob affinityJob);

    void wakeup(AffinityJob affinityJob);

    void shutdown();
}
