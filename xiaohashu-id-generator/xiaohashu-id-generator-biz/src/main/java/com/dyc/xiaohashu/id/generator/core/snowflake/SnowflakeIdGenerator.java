package com.dyc.xiaohashu.id.generator.core.snowflake;

import com.dyc.xiaohashu.id.generator.core.CosId;
import com.dyc.xiaohashu.id.generator.core.IdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.exception.ClockBackwardsException;
import com.dyc.xiaohashu.id.generator.core.snowflake.exception.TimestampOverflowException;

public class SnowflakeIdGenerator implements IdGenerator {

    public static final int TOTAL_BIT = 63;
    public static final int DEFAULT_TIMESTAMP_BIT = 41;
    public static final int DEFAULT_MACHINE_BIT = 10;
    public static final int DEFAULT_SEQUENCE_BIT = 12;
    public static final long DEFAULT_SEQUENCE_RESET_THRESHOLD = ~(-1L << (DEFAULT_SEQUENCE_BIT - 1));

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
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator() {
        this(0);
    }

    public SnowflakeIdGenerator(int machineId) {
        this(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, machineId, DEFAULT_SEQUENCE_RESET_THRESHOLD);
    }

    public SnowflakeIdGenerator(int machineBit, int machineId) {
        this(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, machineBit, DEFAULT_SEQUENCE_BIT, machineId, DEFAULT_SEQUENCE_RESET_THRESHOLD);
    }

    public SnowflakeIdGenerator(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId) {
        this(epoch, timestampBit, machineBit, sequenceBit, machineId, defaultSequenceResetThreshold(sequenceBit));
    }

    public SnowflakeIdGenerator(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId, long sequenceResetThreshold) {
        if ((timestampBit + machineBit + sequenceBit) > TOTAL_BIT) {
            throw new IllegalArgumentException("total bit can't be greater than TOTAL_BIT[63] .");
        }
        this.epoch = epoch;
        this.timestampBit = timestampBit;
        this.machineBit = machineBit;
        this.sequenceBit = sequenceBit;
        this.maxTimestamp = ~(-1L << timestampBit);
        this.maxSequence = ~(-1L << sequenceBit);
        this.maxMachineId = ~(-1 << machineBit);
        if (machineId > this.maxMachineId || machineId < 0) {
            throw new IllegalArgumentException(String.format("machineId[%s] can't be greater than maxMachineId[%s] or less than 0 .", machineId, maxMachineId));
        }
        this.machineLeft = sequenceBit;
        this.timestampLeft = this.machineLeft + machineBit;
        this.machineId = machineId;
        this.sequenceResetThreshold = sequenceResetThreshold;
    }

    @Override
    public synchronized long generate() {
        long currentTimestamp = getCurrentTime();
        if (currentTimestamp < lastTimestamp) {
            throw new ClockBackwardsException(lastTimestamp, currentTimestamp);
        }

        if (currentTimestamp > lastTimestamp && sequence >= sequenceResetThreshold) {
            sequence = 0L;
        }

        sequence = (sequence + 1) & maxSequence;
        if (sequence == 0L) {
            currentTimestamp = nextTime();
        }

        lastTimestamp = currentTimestamp;
        long diffTimestamp = currentTimestamp - epoch;
        if (diffTimestamp > maxTimestamp) {
            throw new TimestampOverflowException(epoch, diffTimestamp, maxTimestamp);
        }
        return diffTimestamp << timestampLeft | machineId << machineLeft | sequence;
    }

    protected long nextTime() {
        long time = getCurrentTime();
        while (time <= lastTimestamp) {
            time = getCurrentTime();
        }
        return time;
    }

    protected long getCurrentTime() {
        return System.currentTimeMillis();
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
}
