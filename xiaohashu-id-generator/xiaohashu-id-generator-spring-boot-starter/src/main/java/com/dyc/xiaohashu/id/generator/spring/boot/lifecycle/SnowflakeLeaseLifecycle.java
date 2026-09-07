package com.dyc.xiaohashu.id.generator.spring.boot.lifecycle;

import com.dyc.xiaohashu.id.generator.core.machine.MachineIdAllocator;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLeaseConfig;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLeaseLostException;
import com.dyc.xiaohashu.id.generator.core.machine.MachineStatus;
import com.dyc.xiaohashu.id.generator.core.snowflake.DefaultSnowflakeIdGenerator;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Maintains Snowflake machine leases during the Spring application lifecycle.
 */
public class SnowflakeLeaseLifecycle implements SmartLifecycle {

    private final DefaultSnowflakeIdGenerator generator;
    private final MachineIdAllocator machineIdAllocator;
    private final MachineLeaseConfig leaseConfig;
    private final Duration shutdownTimeout;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger heartbeatFailures = new AtomicInteger();
    private final AtomicLong heartbeatSuccessTotal = new AtomicLong();
    private final AtomicLong heartbeatFailureTotal = new AtomicLong();
    private final AtomicLong leaseLostTotal = new AtomicLong();
    private final AtomicLong releaseFailureTotal = new AtomicLong();
    private volatile Instant lastHeartbeatAt;
    private volatile Instant lastHeartbeatFailureAt;
    private volatile String lastHeartbeatFailureMessage;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> heartbeatTask;

    public SnowflakeLeaseLifecycle(
            DefaultSnowflakeIdGenerator generator,
            MachineIdAllocator machineIdAllocator,
            MachineLeaseConfig leaseConfig
    ) {
        this(generator, machineIdAllocator, leaseConfig, Duration.ofSeconds(5));
    }

    public SnowflakeLeaseLifecycle(
            DefaultSnowflakeIdGenerator generator,
            MachineIdAllocator machineIdAllocator,
            MachineLeaseConfig leaseConfig,
            Duration shutdownTimeout
    ) {
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        this.machineIdAllocator = Objects.requireNonNull(machineIdAllocator, "machineIdAllocator must not be null");
        this.leaseConfig = Objects.requireNonNull(leaseConfig, "leaseConfig must not be null");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout must not be null");
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());
        long intervalMillis = leaseConfig.heartbeatInterval().toMillis();
        heartbeatTask = executor.scheduleWithFixedDelay(this::heartbeat, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        ScheduledFuture<?> currentTask = heartbeatTask;
        if (currentTask != null) {
            currentTask.cancel(false);
        }
        ScheduledExecutorService currentExecutor = executor;
        if (currentExecutor != null) {
            shutdownGracefully(currentExecutor);
        }
        MachineLease leaseToRelease = generator.machineLease();
        expireCurrentLease();
        releaseLease(leaseToRelease);
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * Returns consecutive heartbeat failures since the latest success.
     *
     * @return consecutive heartbeat failures
     */
    public int consecutiveHeartbeatFailures() {
        return heartbeatFailures.get();
    }

    /**
     * Returns successful heartbeat count.
     *
     * @return successful heartbeats
     */
    public long heartbeatSuccessTotal() {
        return heartbeatSuccessTotal.get();
    }

    /**
     * Returns failed heartbeat count.
     *
     * @return failed heartbeats
     */
    public long heartbeatFailureTotal() {
        return heartbeatFailureTotal.get();
    }

    /**
     * Returns the number of explicit lease-lost events.
     *
     * @return lease-lost events
     */
    public long leaseLostTotal() {
        return leaseLostTotal.get();
    }

    /**
     * Returns failed lease release count.
     *
     * @return release failures
     */
    public long releaseFailureTotal() {
        return releaseFailureTotal.get();
    }

    /**
     * Returns the last successful heartbeat time.
     *
     * @return last heartbeat time, or null when no heartbeat has succeeded
     */
    public Instant lastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    /**
     * Returns the last heartbeat failure time.
     *
     * @return last heartbeat failure time, or null when no heartbeat has failed
     */
    public Instant lastHeartbeatFailureAt() {
        return lastHeartbeatFailureAt;
    }

    /**
     * Returns the last heartbeat failure message.
     *
     * @return failure message, or null when no heartbeat has failed
     */
    public String lastHeartbeatFailureMessage() {
        return lastHeartbeatFailureMessage;
    }

    private void heartbeat() {
        if (!running.get()) {
            return;
        }
        try {
            MachineLease refreshedLease = machineIdAllocator.heartbeat(generator.machineLease(), generator.lastTimestamp());
            generator.refreshLease(refreshedLease);
            heartbeatSuccessTotal.incrementAndGet();
            lastHeartbeatAt = Instant.now();
            heartbeatFailures.set(0);
        } catch (MachineLeaseLostException exception) {
            recordHeartbeatFailure(exception);
            leaseLostTotal.incrementAndGet();
            expireCurrentLease();
            running.set(false);
            stopHeartbeatExecutor(false);
        } catch (RuntimeException exception) {
            recordHeartbeatFailure(exception);
            int failures = heartbeatFailures.incrementAndGet();
            if (failures > leaseConfig.maxHeartbeatFailures()) {
                expireCurrentLease();
                running.set(false);
                stopHeartbeatExecutor(false);
            }
        }
    }

    private void stopHeartbeatExecutor(boolean wait) {
        ScheduledFuture<?> currentTask = heartbeatTask;
        if (currentTask != null) {
            currentTask.cancel(false);
        }
        ScheduledExecutorService currentExecutor = executor;
        if (currentExecutor != null) {
            if (wait) {
                shutdownGracefully(currentExecutor);
            } else {
                currentExecutor.shutdown();
            }
        }
    }

    private void releaseLease(MachineLease lease) {
        try {
            machineIdAllocator.release(lease);
        } catch (RuntimeException exception) {
            releaseFailureTotal.incrementAndGet();
            // Shutdown must continue; a stale or already reclaimed lease is safe to ignore here.
        }
    }

    private void recordHeartbeatFailure(RuntimeException exception) {
        heartbeatFailureTotal.incrementAndGet();
        lastHeartbeatFailureAt = Instant.now();
        lastHeartbeatFailureMessage = exception.getMessage();
    }

    private void shutdownGracefully(ScheduledExecutorService currentExecutor) {
        currentExecutor.shutdown();
        try {
            if (!currentExecutor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                currentExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            currentExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void expireCurrentLease() {
        MachineLease currentLease = generator.machineLease();
        Instant now = Instant.now();
        MachineLease expiredLease = new MachineLease(
                currentLease.namespace(),
                currentLease.machineId(),
                currentLease.instanceIdentity(),
                MachineStatus.EXPIRED,
                Math.max(currentLease.lastTimestamp(), generator.lastTimestamp()),
                currentLease.leaseAcquiredAt(),
                now,
                currentLease.version()
        );
        generator.refreshLease(expiredLease);
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "snowflake-machine-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
    }
}
