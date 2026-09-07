package com.dyc.xiaohashu.id.generator.core.machine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultMachineIdGuarder implements MachineIdGuarder {

    private static final Logger log = LoggerFactory.getLogger(DefaultMachineIdGuarder.class);

    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofMinutes(1);
    public static final Duration DEFAULT_DELAY = Duration.ofMinutes(1);

    private final ConcurrentHashMap<NamespacedInstanceId, GuardianState> guardianStates;
    private final MachineIdAllocator machineIdAllocator;
    private final ScheduledExecutorService executorService;
    private final Duration initialDelay;
    private final Duration delay;
    private final Duration safeGuardDuration;
    private volatile ScheduledFuture<?> scheduledFuture;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DefaultMachineIdGuarder(MachineIdAllocator machineIdAllocator, Duration safeGuardDuration) {
        this(machineIdAllocator, executorService(), DEFAULT_INITIAL_DELAY, DEFAULT_DELAY, safeGuardDuration);
    }

    public DefaultMachineIdGuarder(MachineIdAllocator machineIdAllocator,
                                   ScheduledExecutorService executorService,
                                   Duration initialDelay,
                                   Duration delay,
                                   Duration safeGuardDuration) {
        this.guardianStates = new ConcurrentHashMap<>();
        this.machineIdAllocator = machineIdAllocator;
        this.executorService = executorService;
        this.initialDelay = initialDelay;
        this.delay = delay;
        this.safeGuardDuration = safeGuardDuration;
    }

    public static ScheduledExecutorService executorService() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("MachineIdGuarder");
            return thread;
        });
    }

    @Override
    public Map<NamespacedInstanceId, GuardianState> getGuardianStates() {
        return Map.copyOf(guardianStates);
    }

    @Override
    public void register(String namespace, InstanceId instanceId) {
        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace can not be empty!");
        }
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(namespace, instanceId);
        boolean absent = guardianStates.put(namespacedInstanceId, GuardianState.INITIAL) == null;
        log.debug("Register Instance:[{}] - [{}].", namespacedInstanceId, absent);
    }

    @Override
    public void unregister(String namespace, InstanceId instanceId) {
        guardianStates.remove(new NamespacedInstanceId(namespace, instanceId));
    }

    @Override
    public void start() {
        log.debug("Start registered Instances:[{}].", guardianStates.size());
        if (running.compareAndSet(false, true)) {
            scheduledFuture = executorService.scheduleWithFixedDelay(this::safeGuard, initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    void safeGuard() {
        log.debug("Safe guard registered Instances:[{}].", guardianStates.size());
        for (NamespacedInstanceId registeredInstance : guardianStates.keySet()) {
            long guardAt = System.currentTimeMillis();
            try {
                machineIdAllocator.guard(registeredInstance.getNamespace(), registeredInstance.getInstanceId(), safeGuardDuration);
                guardianStates.put(registeredInstance, GuardianState.success(guardAt));
            } catch (Throwable throwable) {
                guardianStates.put(registeredInstance, GuardianState.failed(guardAt, throwable));
                log.error("Guard Failed:[{}]!", throwable.getMessage(), throwable);
            }
        }
    }

    @Override
    public void stop() {
        log.debug("Stop registered Instances:[{}].", guardianStates.size());
        if (running.compareAndSet(true, false)) {
            scheduledFuture.cancel(true);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
