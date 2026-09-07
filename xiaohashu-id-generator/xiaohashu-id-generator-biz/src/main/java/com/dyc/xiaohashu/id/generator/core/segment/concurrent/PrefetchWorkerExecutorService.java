package com.dyc.xiaohashu.id.generator.core.segment.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Migrated from CosId's PrefetchWorkerExecutorService.
 */
public class PrefetchWorkerExecutorService {

    private static final Logger log = LoggerFactory.getLogger(PrefetchWorkerExecutorService.class);
    public static final Duration DEFAULT_PREFETCH_PERIOD = Duration.ofSeconds(1);
    public static final PrefetchWorkerExecutorService DEFAULT;

    static {
        DEFAULT = new PrefetchWorkerExecutorService(DEFAULT_PREFETCH_PERIOD, Runtime.getRuntime().availableProcessors());
    }

    private volatile boolean shutdown = false;
    private final int corePoolSize;
    private final Duration prefetchPeriod;
    private final DefaultPrefetchWorker[] workers;
    private boolean initialized = false;
    private final AtomicLong threadIdx = new AtomicLong();

    public PrefetchWorkerExecutorService(Duration prefetchPeriod, int corePoolSize) {
        this(prefetchPeriod, corePoolSize, true);
    }

    public PrefetchWorkerExecutorService(Duration prefetchPeriod, int corePoolSize, boolean shutdownHook) {
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException(String.format("corePoolSize:[%s] must be greater than 0.", corePoolSize));
        }
        this.prefetchPeriod = prefetchPeriod;
        this.corePoolSize = corePoolSize;
        this.workers = new DefaultPrefetchWorker[corePoolSize];
        if (shutdownHook) {
            Runtime.getRuntime().addShutdownHook(new GracefullyCloser());
        }
    }

    private void ensureInitWorkers() {
        if (initialized) {
            return;
        }
        initialized = true;
        for (int i = 0; i < corePoolSize; i++) {
            final DefaultPrefetchWorker prefetchWorker = new DefaultPrefetchWorker(prefetchPeriod);
            prefetchWorker.setDaemon(true);
            workers[i] = prefetchWorker;
            if (log.isDebugEnabled()) {
                log.debug("Init Workers - [{}].", prefetchWorker.getName());
            }
        }
    }

    public void shutdown() {
        if (log.isInfoEnabled()) {
            log.info("Shutdown!");
        }
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

    public void submit(AffinityJob affinityJob) {
        Objects.requireNonNull(affinityJob, "affinityJob can not be null!");
        if (log.isInfoEnabled()) {
            log.info("Submit jobId:[{}].", affinityJob.getJobId());
        }
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
            DefaultPrefetchWorker prefetchWorker = chooseWorker();
            if (log.isInfoEnabled()) {
                log.info("Submit jobId:[{}] is bound to thread:[{}].", affinityJob.getJobId(), prefetchWorker.getName());
            }

            if (Thread.State.NEW.equals(prefetchWorker.getState())) {
                if (log.isInfoEnabled()) {
                    log.info("Submit jobId:[{}] is bound to thread:[{}] start.", affinityJob.getJobId(), prefetchWorker.getName());
                }
                prefetchWorker.start();
            }
            prefetchWorker.submit(affinityJob);
            affinityJob.setPrefetchWorker(prefetchWorker);
        }
    }

    private DefaultPrefetchWorker chooseWorker() {
        return workers[(int) Math.abs(threadIdx.getAndIncrement() % workers.length)];
    }

    public class GracefullyCloser extends Thread {
        @Override
        public void run() {
            if (log.isInfoEnabled()) {
                log.info("Close gracefully!");
            }
            shutdown();
        }
    }
}
