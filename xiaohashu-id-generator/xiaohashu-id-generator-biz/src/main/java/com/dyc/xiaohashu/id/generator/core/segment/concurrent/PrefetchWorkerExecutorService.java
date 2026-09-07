package com.dyc.xiaohashu.id.generator.core.segment.concurrent;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Copied and modified from CosId's PrefetchWorkerExecutorService.
 */
public class PrefetchWorkerExecutorService implements AutoCloseable {

    public static final Duration DEFAULT_PREFETCH_PERIOD = Duration.ofSeconds(1);

    private final int corePoolSize;
    private final Duration prefetchPeriod;
    private final DefaultPrefetchWorker[] workers;
    private final AtomicLong threadIdx = new AtomicLong();
    private volatile boolean shutdown = false;
    private boolean initialized = false;

    public PrefetchWorkerExecutorService(Duration prefetchPeriod, int corePoolSize) {
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("corePoolSize must be greater than 0.");
        }
        this.prefetchPeriod = prefetchPeriod;
        this.corePoolSize = corePoolSize;
        this.workers = new DefaultPrefetchWorker[corePoolSize];
    }

    public void submit(AffinityJob affinityJob) {
        if (shutdown) {
            throw new IllegalStateException("PrefetchWorkerExecutorService is shutdown.");
        }
        if (affinityJob.getPrefetchWorker() != null) {
            return;
        }
        synchronized (this) {
            if (affinityJob.getPrefetchWorker() != null) {
                return;
            }
            ensureInitWorkers();
            DefaultPrefetchWorker worker = chooseWorker();
            if (Thread.State.NEW.equals(worker.getState())) {
                worker.start();
            }
            worker.submit(affinityJob);
            affinityJob.setPrefetchWorker(worker);
        }
    }

    private void ensureInitWorkers() {
        if (initialized) {
            return;
        }
        initialized = true;
        for (int i = 0; i < corePoolSize; i++) {
            DefaultPrefetchWorker worker = new DefaultPrefetchWorker(prefetchPeriod);
            worker.setDaemon(true);
            workers[i] = worker;
        }
    }

    private DefaultPrefetchWorker chooseWorker() {
        return workers[(int) Math.abs(threadIdx.getAndIncrement() % corePoolSize)];
    }

    public void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        for (DefaultPrefetchWorker worker : workers) {
            if (worker != null) {
                worker.shutdown();
            }
        }
    }

    @Override
    public void close() {
        shutdown();
    }
}
