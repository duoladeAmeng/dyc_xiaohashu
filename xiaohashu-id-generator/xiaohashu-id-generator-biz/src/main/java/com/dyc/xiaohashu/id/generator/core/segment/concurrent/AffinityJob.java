package com.dyc.xiaohashu.id.generator.core.segment.concurrent;

/**
 * Copied and modified from CosId's AffinityJob.
 */
public interface AffinityJob extends Runnable {

    String getJobId();

    default String affinity() {
        return getJobId();
    }

    default void hungry() {
        setHungerTime(System.currentTimeMillis() / 1000);
        PrefetchWorker worker = getPrefetchWorker();
        if (worker != null) {
            worker.wakeup(this);
        }
    }

    void setHungerTime(long hungerTime);

    PrefetchWorker getPrefetchWorker();

    void setPrefetchWorker(PrefetchWorker prefetchWorker);
}
