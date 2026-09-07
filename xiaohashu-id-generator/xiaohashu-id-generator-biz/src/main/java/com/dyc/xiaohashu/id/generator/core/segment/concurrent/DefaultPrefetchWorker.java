package com.dyc.xiaohashu.id.generator.core.segment.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Migrated from CosId's DefaultPrefetchWorker.
 */
public class DefaultPrefetchWorker extends Thread implements PrefetchWorker {

    private static final Logger log = LoggerFactory.getLogger(DefaultPrefetchWorker.class);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private volatile boolean shutdown = false;
    private final Duration prefetchPeriod;
    private final CopyOnWriteArraySet<AffinityJob> affinityJobs = new CopyOnWriteArraySet<>();

    public DefaultPrefetchWorker(Duration prefetchPeriod) {
        super("DefaultPrefetchWorker-" + THREAD_COUNTER.incrementAndGet());
        this.prefetchPeriod = prefetchPeriod;
    }

    @Override
    public void shutdown() {
        if (log.isInfoEnabled()) {
            log.info("Shutdown!");
        }
        if (shutdown) {
            return;
        }
        shutdown = true;
    }

    @Override
    public void submit(AffinityJob affinityJob) {
        if (log.isInfoEnabled()) {
            log.info("Submit [{}] jobSize:[{}].", affinityJob.getJobId(), affinityJobs.size());
        }

        if (shutdown) {
            throw new IllegalArgumentException("PrefetchWorker is shutdown.");
        }
        affinityJobs.add(affinityJob);
    }

    @Override
    public void cancel(AffinityJob affinityJob) {
        if (log.isInfoEnabled()) {
            log.info("Cancel [{}] jobSize:[{}].", affinityJob.getJobId(), affinityJobs.size());
        }
        affinityJobs.remove(affinityJob);
    }

    @Override
    public void wakeup(AffinityJob affinityJob) {
        if (log.isDebugEnabled()) {
            log.debug("Wakeup [{}] - state:[{}].", affinityJob.getJobId(), this.getState());
        }
        if (shutdown) {
            if (log.isWarnEnabled()) {
                log.warn("Wakeup [{}] - PrefetchWorker is shutdown,Can't be awakened!", affinityJob.getJobId());
            }
            return;
        }

        if (State.RUNNABLE.equals(this.getState())) {
            if (log.isDebugEnabled()) {
                log.debug("Wakeup [{}] - PrefetchWorker is running ,Don't need to be awakened.", affinityJob.getJobId());
            }
            return;
        }
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
                        if (log.isErrorEnabled()) {
                            log.error(throwable.getMessage(), throwable);
                        }
                    }
                });
                LockSupport.parkNanos(this, prefetchPeriod.toNanos());
            } catch (Throwable throwable) {
                if (log.isErrorEnabled()) {
                    log.error(throwable.getMessage(), throwable);
                }
            }
        }
    }
}
