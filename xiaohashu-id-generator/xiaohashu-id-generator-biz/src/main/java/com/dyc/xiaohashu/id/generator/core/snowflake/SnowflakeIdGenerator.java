package com.dyc.xiaohashu.id.generator.core.snowflake;

import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;
import com.dyc.xiaohashu.id.generator.core.IdGenerator;
import com.dyc.xiaohashu.id.generator.core.machine.ClockBackwardsSynchronizer;
import com.dyc.xiaohashu.id.generator.core.machine.DefaultClockBackwardsSynchronizer;
import com.dyc.xiaohashu.id.generator.core.machine.InstanceId;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdAllocator;
import com.dyc.xiaohashu.id.generator.core.machine.MachineState;
import com.dyc.xiaohashu.id.generator.core.snowflake.exception.TimestampOverflowException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Copied and modified from CosId's SnowflakeId / AbstractSnowflakeId / MillisecondSnowflakeId.
 * JDBC only participates in machineId acquire, guard and release.
 */
public class SnowflakeIdGenerator implements IdGenerator, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);
    public static final int TOTAL_BIT = 63;
    public static final int DEFAULT_TIMESTAMP_BIT = 41;
    public static final int DEFAULT_MACHINE_BIT = 10;
    public static final int DEFAULT_SEQUENCE_BIT = 12;

    private final String namespace;
    private final InstanceId instanceId;
    private final MachineIdAllocator machineIdAllocator;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;
    private final Duration safeGuardDuration;
    private final ScheduledExecutorService heartbeatExecutor;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong generatedTotal = new AtomicLong();
    private final AtomicLong sequenceOverflowTotal = new AtomicLong();
    private final AtomicLong clockBackwardsTotal = new AtomicLong();
    private final AtomicLong heartbeatFailureTotal = new AtomicLong();
    private final AtomicLong leaseExpiresAt = new AtomicLong();

    private final long epoch;
    private final int timestampBit;
    private final int machineBit;
    private final int sequenceBit;
    private final long maxTimestamp;
    private final long maxSequence;
    private final int maxMachineId;
    private final long machineLeft;
    private final long timestampLeft;
    private final long machineId;
    private final long sequenceResetThreshold;

    private long sequence = 0L;
    private volatile long lastTimestamp = -1L;

    public SnowflakeIdGenerator() {
        this(System.currentTimeMillis(), DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, 0);
    }

    public SnowflakeIdGenerator(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId) {
        this(epoch, timestampBit, machineBit, sequenceBit, machineId, defaultSequenceResetThreshold(sequenceBit));
    }

    public SnowflakeIdGenerator(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId, long sequenceResetThreshold) {
        this.namespace = "manual";
        this.instanceId = InstanceId.of("manual", true);
        this.machineIdAllocator = null;
        this.clockBackwardsSynchronizer = new DefaultClockBackwardsSynchronizer();
        this.safeGuardDuration = Duration.ofDays(3650);
        this.heartbeatExecutor = null;
        if ((timestampBit + machineBit + sequenceBit) > TOTAL_BIT) {
            throw new IllegalArgumentException("total bit can't be greater than TOTAL_BIT[63].");
        }
        this.epoch = epoch;
        this.timestampBit = timestampBit;
        this.machineBit = machineBit;
        this.sequenceBit = sequenceBit;
        this.maxTimestamp = ~(-1L << timestampBit);
        this.maxSequence = ~(-1L << sequenceBit);
        this.maxMachineId = ~(-1 << machineBit);
        if (machineId > this.maxMachineId || machineId < 0) {
            throw new IllegalArgumentException("machineId " + machineId + " can't be greater than maxMachineId " + maxMachineId + " or less than 0.");
        }
        this.machineLeft = sequenceBit;
        this.timestampLeft = this.machineLeft + machineBit;
        this.machineId = machineId;
        this.sequenceResetThreshold = sequenceResetThreshold;
        this.leaseExpiresAt.set(Long.MAX_VALUE);
    }

    public SnowflakeIdGenerator(String namespace,
                                long epoch,
                                int timestampBit,
                                int machineBit,
                                int sequenceBit,
                                InstanceId instanceId,
                                MachineIdAllocator machineIdAllocator,
                                Duration heartbeatInterval,
                                Duration safeGuardDuration,
                                ClockBackwardsSynchronizer clockBackwardsSynchronizer,
                                MeterRegistry meterRegistry) {
        if ((timestampBit + machineBit + sequenceBit) > TOTAL_BIT) {
            throw new IllegalArgumentException("total bit can't be greater than TOTAL_BIT[63].");
        }
        if (heartbeatInterval.compareTo(safeGuardDuration) >= 0) {
            throw new IllegalArgumentException("heartbeatInterval must be less than safeGuardDuration.");
        }
        this.namespace = requireText(namespace, "namespace");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId can not be null.");
        this.machineIdAllocator = Objects.requireNonNull(machineIdAllocator, "machineIdAllocator can not be null.");
        this.clockBackwardsSynchronizer = Objects.requireNonNullElseGet(clockBackwardsSynchronizer, DefaultClockBackwardsSynchronizer::new);
        this.safeGuardDuration = safeGuardDuration;
        this.epoch = epoch;
        this.timestampBit = timestampBit;
        this.machineBit = machineBit;
        this.sequenceBit = sequenceBit;
        this.maxTimestamp = ~(-1L << timestampBit);
        this.maxSequence = ~(-1L << sequenceBit);
        this.maxMachineId = ~(-1 << machineBit);
        this.machineLeft = sequenceBit;
        this.timestampLeft = this.machineLeft + machineBit;
        this.sequenceResetThreshold = defaultSequenceResetThreshold(sequenceBit);

        MachineState machineState = machineIdAllocator.acquire(namespace, machineBit, instanceId, safeGuardDuration);
        if (machineState.getMachineId() > this.maxMachineId || machineState.getMachineId() < 0) {
            throw new IllegalArgumentException("allocated machineId is out of range.");
        }
        clockBackwardsSynchronizer.syncUninterruptibly(machineState.getLastTimestamp());
        this.machineId = machineState.getMachineId();
        this.lastTimestamp = Math.max(machineState.getLastTimestamp(), System.currentTimeMillis());
        this.leaseExpiresAt.set(System.currentTimeMillis() + safeGuardDuration.toMillis());
        registerMetrics(meterRegistry);
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "id-generator-snowflake-heartbeat-" + namespace);
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor.scheduleWithFixedDelay(this::guardMachineId, heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Acquired snowflake machineId {} for {}@{}.", machineId, instanceId, namespace);
    }

    @Override
    public long nextId() {
        if (closed.get()) {
            throw new GeneratorUnavailableException("Snowflake generator is closed.");
        }
        if (System.currentTimeMillis() > leaseExpiresAt.get()) {
            throw new GeneratorUnavailableException("Snowflake machineId lease expired. Refusing to generate id.");
        }
        long id = generate();
        generatedTotal.incrementAndGet();
        return id;
    }

    private synchronized long generate() {
        long currentTimestamp = System.currentTimeMillis();
        if (currentTimestamp < lastTimestamp) {
            clockBackwardsTotal.incrementAndGet();
            clockBackwardsSynchronizer.syncUninterruptibly(lastTimestamp);
            currentTimestamp = System.currentTimeMillis();
        }

        if (currentTimestamp > lastTimestamp && sequence >= sequenceResetThreshold) {
            sequence = 0L;
        }

        sequence = (sequence + 1) & maxSequence;
        if (sequence == 0L) {
            sequenceOverflowTotal.incrementAndGet();
            currentTimestamp = nextTime();
        }

        lastTimestamp = currentTimestamp;
        long diffTimestamp = currentTimestamp - epoch;
        if (diffTimestamp > maxTimestamp) {
            throw new TimestampOverflowException(epoch, diffTimestamp, maxTimestamp);
        }
        return diffTimestamp << timestampLeft | machineId << machineLeft | sequence;
    }

    private long nextTime() {
        long time = System.currentTimeMillis();
        while (time <= lastTimestamp) {
            time = System.currentTimeMillis();
        }
        return time;
    }

    private void guardMachineId() {
        if (closed.get() || machineIdAllocator == null) {
            return;
        }
        try {
            machineIdAllocator.guard(namespace, instanceId, MachineState.of((int) machineId, lastTimestamp), safeGuardDuration);
            leaseExpiresAt.set(System.currentTimeMillis() + safeGuardDuration.toMillis());
        } catch (Throwable throwable) {
            heartbeatFailureTotal.incrementAndGet();
            log.warn("Guard snowflake machineId failed for {}@{}.", instanceId, namespace, throwable);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
        if (machineIdAllocator != null) {
            machineIdAllocator.release(namespace, instanceId, MachineState.of((int) machineId, lastTimestamp));
        }
    }

    private void registerMetrics(MeterRegistry registry) {
        if (registry == null) {
            return;
        }
        Gauge.builder("id_generator_snowflake_generated_total", generatedTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_snowflake_sequence_overflow_total", sequenceOverflowTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_snowflake_clock_backwards_total", clockBackwardsTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_snowflake_heartbeat_failure_total", heartbeatFailureTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_snowflake_machine_id", this, SnowflakeIdGenerator::getMachineId).register(registry);
        Gauge.builder("id_generator_snowflake_lease_remaining_millis", this, generator -> Math.max(generator.leaseExpiresAt.get() - System.currentTimeMillis(), 0L)).register(registry);
    }

    public long getEpoch() {
        return epoch;
    }

    public int getTimestampBit() {
        return timestampBit;
    }

    public int getMachineBit() {
        return machineBit;
    }

    public int getSequenceBit() {
        return sequenceBit;
    }

    public int getMachineId() {
        return (int) machineId;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }

    public static long defaultSequenceResetThreshold(int sequenceBit) {
        return ~(-1L << (sequenceBit - 1));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " can not be empty.");
        }
        return value;
    }
}
