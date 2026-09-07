package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared daemon scheduler for segment-chain prefetch jobs.
 */
public class DefaultSegmentPrefetchScheduler implements SegmentPrefetchScheduler {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final Duration prefetchPeriod;
    private final Duration shutdownTimeout;
    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService wakeupExecutor;
    private final Map<String, JobRegistration> jobs = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong failedRunTotal = new AtomicLong();

    public DefaultSegmentPrefetchScheduler(Duration prefetchPeriod, int poolSize) {
        this(prefetchPeriod, poolSize, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    public DefaultSegmentPrefetchScheduler(Duration prefetchPeriod, int poolSize, Duration shutdownTimeout) {
        this.prefetchPeriod = Objects.requireNonNull(prefetchPeriod, "prefetchPeriod must not be null");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout must not be null");
        if (prefetchPeriod.isZero() || prefetchPeriod.isNegative()) {
            throw new InvalidIdGeneratorConfigurationException("prefetchPeriod must be greater than zero");
        }
        if (shutdownTimeout.isNegative()) {
            throw new InvalidIdGeneratorConfigurationException("shutdownTimeout must not be negative");
        }
        if (poolSize <= 0) {
            throw new InvalidIdGeneratorConfigurationException("poolSize must be greater than zero");
        }
        this.scheduledExecutor = Executors.newScheduledThreadPool(poolSize, daemonThreadFactory("segment-prefetch-scheduler-"));
        this.wakeupExecutor = Executors.newFixedThreadPool(poolSize, daemonThreadFactory("segment-prefetch-wakeup-"));
    }

    @Override
    public void register(String jobId, Runnable job) {
        requireText(jobId, "jobId");
        Objects.requireNonNull(job, "job must not be null");
        if (closed.get()) {
            throw new IllegalStateException("SegmentPrefetchScheduler is closed");
        }
        JobRegistration registration = new JobRegistration(job);
        JobRegistration previous = jobs.putIfAbsent(jobId, registration);
        if (previous != null) {
            throw new IllegalStateException("prefetch job already registered: " + jobId);
        }
        ScheduledFuture<?> future = scheduledExecutor.scheduleWithFixedDelay(
                () -> registration.requestRun(scheduledExecutor),
                prefetchPeriod.toMillis(),
                prefetchPeriod.toMillis(),
                TimeUnit.MILLISECONDS
        );
        registration.setFuture(future);
    }

    @Override
    public void wakeup(String jobId) {
        JobRegistration registration = jobs.get(jobId);
        if (registration == null || closed.get()) {
            return;
        }
        registration.requestRun(wakeupExecutor);
    }

    @Override
    public void unregister(String jobId) {
        JobRegistration registration = jobs.remove(jobId);
        if (registration != null) {
            registration.cancel();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            jobs.values().forEach(JobRegistration::cancel);
            jobs.clear();
            shutdownGracefully(scheduledExecutor);
            shutdownGracefully(wakeupExecutor);
        }
    }

    /**
     * Returns the number of failed job executions observed by this scheduler.
     *
     * @return failed job executions
     */
    public long failedRunTotal() {
        return failedRunTotal.get();
    }

    /**
     * Returns the number of currently registered jobs.
     *
     * @return registered job count
     */
    public int registeredJobCount() {
        return jobs.size();
    }

    /**
     * Returns whether this scheduler has been closed.
     *
     * @return true when closed
     */
    public boolean closed() {
        return closed.get();
    }

    private void shutdownGracefully(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new InvalidIdGeneratorConfigurationException(name + " must not be blank");
        }
    }

    private final class JobRegistration {

        private final Runnable job;
        private final AtomicBoolean inFlight = new AtomicBoolean();
        private final AtomicBoolean runQueued = new AtomicBoolean();
        private volatile ScheduledFuture<?> future;

        private JobRegistration(Runnable job) {
            this.job = job;
        }

        private void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        private void requestRun(Executor executor) {
            if (runQueued.compareAndSet(false, true)) {
                executor.execute(this::runOnce);
            }
        }

        private void runOnce() {
            if (!inFlight.compareAndSet(false, true)) {
                return;
            }
            try {
                job.run();
            } catch (RuntimeException exception) {
                failedRunTotal.incrementAndGet();
            } finally {
                inFlight.set(false);
                runQueued.set(false);
            }
        }

        private void cancel() {
            ScheduledFuture<?> scheduledFuture = future;
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
        }
    }
}
