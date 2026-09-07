package com.dyc.xiaohashu.id.generator.core.segment.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Copied and modified from CosId's DefaultPrefetchWorker.
 */
public class DefaultPrefetchWorker extends Thread implements PrefetchWorker {

    private static final Logger log = LoggerFactory.getLogger(DefaultPrefetchWorker.class);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final Duration prefetchPeriod;
    private final CopyOnWriteArraySet<AffinityJob> affinityJobs = new CopyOnWriteArraySet<>();
    private volatile boolean shutdown = false;

    public DefaultPrefetchWorker(Duration prefetchPeriod) {
        super("DefaultPrefetchWorker-" + THREAD_COUNTER.incrementAndGet());
        this.prefetchPeriod = prefetchPeriod;
    }

    @Override
    public void submit(AffinityJob affinityJob) {
        if (shutdown) {
            throw new IllegalStateException("PrefetchWorker is shutdown.");
        }
        affinityJobs.add(affinityJob);
    }

    @Override
    public void cancel(AffinityJob affinityJob) {
        affinityJobs.remove(affinityJob);
    }

    @Override
    public void wakeup(AffinityJob affinityJob) {
        if (!shutdown && !State.RUNNABLE.equals(getState())) {
            LockSupport.unpark(this);
        }
    }

    @Override
    public void shutdown() {
        shutdown = true;
        LockSupport.unpark(this);
    }

    @Override
    public void run() {
        while (!shutdown) {
            try {
                affinityJobs.forEach(job -> {
                    try {
                        job.run();
                    } catch (Throwable throwable) {
                        log.warn("Segment prefetch job {} failed.", job.getJobId(), throwable);
                    }
                });
                LockSupport.parkNanos(this, prefetchPeriod.toNanos());
            } catch (Throwable throwable) {
                log.warn("Segment prefetch worker failed.", throwable);
            }
        }
    }
}
