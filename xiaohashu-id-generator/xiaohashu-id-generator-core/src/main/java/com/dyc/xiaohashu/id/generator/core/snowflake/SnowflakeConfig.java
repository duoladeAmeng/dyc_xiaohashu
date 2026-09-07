package com.dyc.xiaohashu.id.generator.core.snowflake;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable bit-layout configuration for a Snowflake ID generator.
 *
 * @param epoch custom epoch used as timestamp zero
 * @param timestampBits number of timestamp bits
 * @param machineBits number of machine ID bits
 * @param sequenceBits number of per-millisecond sequence bits
 * @param sequenceResetThreshold threshold used when resetting sequence on a new millisecond
 * @param clockBackwardsPolicy local clock rollback policy
 */
public record SnowflakeConfig(
        Instant epoch,
        int timestampBits,
        int machineBits,
        int sequenceBits,
        long sequenceResetThreshold,
        ClockBackwardsPolicy clockBackwardsPolicy
) {

    public static final int TOTAL_BITS = 63;
    public static final int DEFAULT_TIMESTAMP_BITS = 41;
    public static final int DEFAULT_MACHINE_BITS = 10;
    public static final int DEFAULT_SEQUENCE_BITS = 12;
    public static final Instant DEFAULT_EPOCH = Instant.parse("2025-01-01T00:00:00Z");

    public SnowflakeConfig {
        Objects.requireNonNull(epoch, "epoch must not be null");
        Objects.requireNonNull(clockBackwardsPolicy, "clockBackwardsPolicy must not be null");
        requirePositive(timestampBits, "timestampBits");
        requirePositive(machineBits, "machineBits");
        requirePositive(sequenceBits, "sequenceBits");
        if (machineBits >= Integer.SIZE) {
            throw new InvalidIdGeneratorConfigurationException("machineBits must be less than 32");
        }
        if (timestampBits + machineBits + sequenceBits > TOTAL_BITS) {
            throw new InvalidIdGeneratorConfigurationException("timestampBits + machineBits + sequenceBits must not exceed 63");
        }
        long maxSequence = maxValue(sequenceBits);
        if (sequenceResetThreshold < 0 || sequenceResetThreshold > maxSequence) {
            throw new InvalidIdGeneratorConfigurationException("sequenceResetThreshold must be between 0 and maxSequence");
        }
    }

    /**
     * Returns the default production configuration.
     *
     * @return default Snowflake config
     */
    public static SnowflakeConfig defaults() {
        return new SnowflakeConfig(
                DEFAULT_EPOCH,
                DEFAULT_TIMESTAMP_BITS,
                DEFAULT_MACHINE_BITS,
                DEFAULT_SEQUENCE_BITS,
                defaultSequenceResetThreshold(DEFAULT_SEQUENCE_BITS),
                ClockBackwardsPolicy.DEFAULT
        );
    }

    /**
     * Validates this configuration against a concrete startup time.
     *
     * @param now startup time
     */
    public void validateUsableAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        long nowMillis = now.toEpochMilli();
        long epochMillis = epoch.toEpochMilli();
        if (epochMillis >= nowMillis) {
            throw new InvalidIdGeneratorConfigurationException("epoch must be earlier than current time");
        }
        if (nowMillis - epochMillis > maxTimestamp()) {
            throw new InvalidIdGeneratorConfigurationException("current time is outside timestamp bit range");
        }
    }

    /**
     * Returns the largest timestamp delta representable by this layout.
     *
     * @return max timestamp delta
     */
    public long maxTimestamp() {
        return maxValue(timestampBits);
    }

    /**
     * Returns the largest machine ID representable by this layout.
     *
     * @return max machine ID
     */
    public int maxMachineId() {
        return (int) maxValue(machineBits);
    }

    /**
     * Returns the largest sequence value representable by this layout.
     *
     * @return max sequence
     */
    public long maxSequence() {
        return maxValue(sequenceBits);
    }

    /**
     * Returns CosId-compatible default sequence reset threshold.
     *
     * @param sequenceBits sequence bit count
     * @return default sequence reset threshold
     */
    public static long defaultSequenceResetThreshold(int sequenceBits) {
        requirePositive(sequenceBits, "sequenceBits");
        return maxValue(sequenceBits) >>> 1;
    }

    private static long maxValue(int bits) {
        return -1L >>> (Long.SIZE - bits);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new InvalidIdGeneratorConfigurationException(name + " must be greater than zero");
        }
    }
}
