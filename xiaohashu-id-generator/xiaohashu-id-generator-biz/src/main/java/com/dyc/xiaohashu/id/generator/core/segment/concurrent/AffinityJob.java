package com.dyc.xiaohashu.id.generator.core.segment.concurrent;

import com.dyc.xiaohashu.id.generator.core.segment.Clock;

/**
 * Migrated from CosId's AffinityJob.
 */
public interface AffinityJob extends Runnable {

    String getJobId();

    default String affinity() {
        return getJobId();
    }

    default void hungry() {
        setHungerTime(Clock.CACHE.secondTime());
        getPrefetchWorker().wakeup(this);
    }

    void setHungerTime(long hungerTime);

    PrefetchWorker getPrefetchWorker();

    void setPrefetchWorker(PrefetchWorker prefetchWorker);
}
