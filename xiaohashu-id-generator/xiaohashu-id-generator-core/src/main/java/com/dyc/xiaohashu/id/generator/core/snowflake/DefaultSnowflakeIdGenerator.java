package com.dyc.xiaohashu.id.generator.core.snowflake;

import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;
import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Default Snowflake ID generator backed by a machine lease snapshot.
 */
public class DefaultSnowflakeIdGenerator implements SnowflakeIdGenerator {

    private final SnowflakeConfig config;
    private final TimeService timeService;
    private final ClockBackwardsHandler clockBackwardsHandler;
    private final AtomicReference<MachineLease> machineLeaseRef;
    private final LongAdder generatedTotal = new LongAdder();
    private final LongAdder clockBackwardsTotal = new LongAdder();
    private final LongAdder sequenceOverflowTotal = new LongAdder();
    private final int timestampShift;
    private final int machineShift;
    private long sequence;
    private long lastTimestamp;

    public DefaultSnowflakeIdGenerator(
            SnowflakeConfig config,
            MachineLease machineLease,
            TimeService timeService
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.timeService = Objects.requireNonNull(timeService, "timeService must not be null");
        this.clockBackwardsHandler = new DefaultClockBackwardsHandler(config.clockBackwardsPolicy(), timeService);
        MachineLease checkedLease = validateMachineLease(machineLease);
        long currentTimestamp = this.timeService.currentTimeMillis();
        this.config.validateUsableAt(Instant.ofEpochMilli(currentTimestamp));
        if (checkedLease.lastTimestamp() > currentTimestamp) {
            clockBackwardsTotal.increment();
            this.clockBackwardsHandler.handle(checkedLease.lastTimestamp(), currentTimestamp);
            currentTimestamp = this.timeService.currentTimeMillis();
            if (checkedLease.lastTimestamp() > currentTimestamp) {
                throw new ClockBackwardsException("lease lastTimestamp is greater than current time");
            }
        }
        this.machineLeaseRef = new AtomicReference<>(checkedLease);
        this.timestampShift = config.machineBits() + config.sequenceBits();
        this.machineShift = config.sequenceBits();
        this.sequence = config.sequenceResetThreshold();
        this.lastTimestamp = checkedLease.lastTimestamp();
    }

    @Override
    public synchronized long nextId() {
        MachineLease lease = machineLeaseRef.get();
        long currentTimestamp = timeService.currentTimeMillis();
        Instant now = Instant.ofEpochMilli(currentTimestamp);
        if (!lease.canGenerateAt(now)) {
            throw new GeneratorUnavailableException("machine lease is not active or has expired");
        }
        if (currentTimestamp < lastTimestamp) {
            clockBackwardsTotal.increment();
            clockBackwardsHandler.handle(lastTimestamp, currentTimestamp);
            currentTimestamp = timeService.currentTimeMillis();
            if (currentTimestamp < lastTimestamp) {
                throw new ClockBackwardsException("clock is still behind last generated timestamp");
            }
        }
        if (currentTimestamp > lastTimestamp && sequence >= config.sequenceResetThreshold()) {
            sequence = 0;
        } else {
            sequence = (sequence + 1) & config.maxSequence();
            if (sequence == 0) {
                sequenceOverflowTotal.increment();
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        }
        validateTimestampRange(currentTimestamp);
        lastTimestamp = currentTimestamp;
        generatedTotal.increment();
        long timestampPart = currentTimestamp - config.epoch().toEpochMilli();
        return (timestampPart << timestampShift)
                | ((long) lease.machineId() << machineShift)
                | sequence;
    }

    /**
     * Replaces the lease snapshot after a successful heartbeat.
     *
     * @param refreshedLease refreshed lease from the allocator
     */
    public void refreshLease(MachineLease refreshedLease) {
        MachineLease checkedLease = validateMachineLease(refreshedLease);
        MachineLease currentLease = machineLeaseRef.get();
        if (currentLease.machineId() != checkedLease.machineId()) {
            throw new InvalidIdGeneratorConfigurationException("refreshed lease machineId must not change");
        }
        if (!currentLease.namespace().equals(checkedLease.namespace())) {
            throw new InvalidIdGeneratorConfigurationException("refreshed lease namespace must not change");
        }
        machineLeaseRef.set(checkedLease);
    }

    @Override
    public SnowflakeConfig config() {
        return config;
    }

    @Override
    public MachineLease machineLease() {
        return machineLeaseRef.get();
    }

    @Override
    public synchronized long lastTimestamp() {
        return lastTimestamp;
    }

    @Override
    public long generatedTotal() {
        return generatedTotal.sum();
    }

    @Override
    public long clockBackwardsTotal() {
        return clockBackwardsTotal.sum();
    }

    @Override
    public long sequenceOverflowTotal() {
        return sequenceOverflowTotal.sum();
    }

    private long waitNextMillis(long lastUsedTimestamp) {
        long currentTimestamp = timeService.currentTimeMillis();
        while (currentTimestamp <= lastUsedTimestamp) {
            Thread.onSpinWait();
            currentTimestamp = timeService.currentTimeMillis();
        }
        return currentTimestamp;
    }

    private MachineLease validateMachineLease(MachineLease machineLease) {
        MachineLease checkedLease = Objects.requireNonNull(machineLease, "machineLease must not be null");
        if (checkedLease.machineId() > config.maxMachineId()) {
            throw new InvalidIdGeneratorConfigurationException("machineId exceeds configured machineBits");
        }
        return checkedLease;
    }

    private void validateTimestampRange(long currentTimestamp) {
        long timestampPart = currentTimestamp - config.epoch().toEpochMilli();
        if (timestampPart < 0 || timestampPart > config.maxTimestamp()) {
            throw new TimestampOverflowException("current time is outside timestamp bit range");
        }
    }
}
