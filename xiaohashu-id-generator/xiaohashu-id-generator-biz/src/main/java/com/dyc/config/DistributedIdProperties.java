package com.dyc.config;

import com.dyc.xiaohashu.id.generator.core.CosId;
import com.dyc.xiaohashu.id.generator.core.machine.DefaultClockBackwardsSynchronizer;
import com.dyc.xiaohashu.id.generator.core.machine.DefaultMachineIdGuarder;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Instant;

@Validated
@ConfigurationProperties(prefix = "distributed-id")
public class DistributedIdProperties {

    @NotBlank
    private String namespace = "xiaohashu";

    private String instanceId;

    private boolean stableInstance = false;

    @Valid
    private Jdbc jdbc = new Jdbc();

    @Valid
    private Snowflake snowflake = new Snowflake();

    @Valid
    private Segment segment = new Segment();

    @Valid
    private SegmentChain segmentChain = new SegmentChain();

    public void validate() {
        int totalBit = snowflake.timestampBit + snowflake.machineBit + snowflake.sequenceBit;
        if (totalBit > 63) {
            throw new IllegalArgumentException("snowflake timestampBit + machineBit + sequenceBit must be <= 63.");
        }
        if (!snowflake.epoch.isBefore(Instant.now().plus(Duration.ofDays(1)))) {
            throw new IllegalArgumentException("snowflake epoch is unreasonable.");
        }
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public boolean isStableInstance() {
        return stableInstance;
    }

    public void setStableInstance(boolean stableInstance) {
        this.stableInstance = stableInstance;
    }

    public Jdbc getJdbc() {
        return jdbc;
    }

    public void setJdbc(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    public Snowflake getSnowflake() {
        return snowflake;
    }

    public void setSnowflake(Snowflake snowflake) {
        this.snowflake = snowflake;
    }

    public Segment getSegment() {
        return segment;
    }

    public void setSegment(Segment segment) {
        this.segment = segment;
    }

    public SegmentChain getSegmentChain() {
        return segmentChain;
    }

    public void setSegmentChain(SegmentChain segmentChain) {
        this.segmentChain = segmentChain;
    }

    public static class Jdbc {
        private boolean initializeSchema = true;

        public boolean isInitializeSchema() {
            return initializeSchema;
        }

        public void setInitializeSchema(boolean initializeSchema) {
            this.initializeSchema = initializeSchema;
        }
    }

    public static class Snowflake {
        private boolean enabled = true;
        private Instant epoch = Instant.ofEpochMilli(CosId.COSID_EPOCH);
        @Min(1)
        private int timestampBit = SnowflakeIdGenerator.DEFAULT_TIMESTAMP_BIT;
        @Min(1)
        private int machineBit = SnowflakeIdGenerator.DEFAULT_MACHINE_BIT;
        @Min(1)
        private int sequenceBit = SnowflakeIdGenerator.DEFAULT_SEQUENCE_BIT;
        private long sequenceResetThreshold = SnowflakeIdGenerator.DEFAULT_SEQUENCE_RESET_THRESHOLD;
        private boolean clockSync = true;
        private Duration guarderInitialDelay = DefaultMachineIdGuarder.DEFAULT_INITIAL_DELAY;
        private Duration guarderDelay = DefaultMachineIdGuarder.DEFAULT_DELAY;
        private Duration safeGuardDuration = Duration.ofMinutes(5);
        @Min(1)
        private int clockSpinThreshold = DefaultClockBackwardsSynchronizer.DEFAULT_SPIN_THRESHOLD;
        @Min(2)
        private int clockBrokenThreshold = DefaultClockBackwardsSynchronizer.DEFAULT_BROKEN_THRESHOLD;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Instant getEpoch() {
            return epoch;
        }

        public void setEpoch(Instant epoch) {
            this.epoch = epoch;
        }

        public int getTimestampBit() {
            return timestampBit;
        }

        public void setTimestampBit(int timestampBit) {
            this.timestampBit = timestampBit;
        }

        public int getMachineBit() {
            return machineBit;
        }

        public void setMachineBit(int machineBit) {
            this.machineBit = machineBit;
        }

        public int getSequenceBit() {
            return sequenceBit;
        }

        public void setSequenceBit(int sequenceBit) {
            this.sequenceBit = sequenceBit;
        }

        public long getSequenceResetThreshold() {
            return sequenceResetThreshold;
        }

        public void setSequenceResetThreshold(long sequenceResetThreshold) {
            this.sequenceResetThreshold = sequenceResetThreshold;
        }

        public boolean isClockSync() {
            return clockSync;
        }

        public void setClockSync(boolean clockSync) {
            this.clockSync = clockSync;
        }

        public Duration getGuarderInitialDelay() {
            return guarderInitialDelay;
        }

        public void setGuarderInitialDelay(Duration guarderInitialDelay) {
            this.guarderInitialDelay = guarderInitialDelay;
        }

        public Duration getGuarderDelay() {
            return guarderDelay;
        }

        public void setGuarderDelay(Duration guarderDelay) {
            this.guarderDelay = guarderDelay;
        }

        public Duration getSafeGuardDuration() {
            return safeGuardDuration;
        }

        public void setSafeGuardDuration(Duration safeGuardDuration) {
            this.safeGuardDuration = safeGuardDuration;
        }

        public int getClockSpinThreshold() {
            return clockSpinThreshold;
        }

        public void setClockSpinThreshold(int clockSpinThreshold) {
            this.clockSpinThreshold = clockSpinThreshold;
        }

        public int getClockBrokenThreshold() {
            return clockBrokenThreshold;
        }

        public void setClockBrokenThreshold(int clockBrokenThreshold) {
            this.clockBrokenThreshold = clockBrokenThreshold;
        }
    }

    public static class Segment {
        private boolean enabled = true;
        @NotBlank
        private String name = "segment";
        @Min(1)
        private long step = 10000;
        @Min(1)
        private long ttlSeconds = Long.MAX_VALUE;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getStep() {
            return step;
        }

        public void setStep(long step) {
            this.step = step;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class SegmentChain extends Segment {
        @Min(1)
        private int safeDistance = 2;
        private Duration prefetchPeriod = Duration.ofSeconds(1);
        @Min(1)
        private int workerCorePoolSize = Runtime.getRuntime().availableProcessors();

        public SegmentChain() {
            setName("segment-chain");
        }

        public int getSafeDistance() {
            return safeDistance;
        }

        public void setSafeDistance(int safeDistance) {
            this.safeDistance = safeDistance;
        }

        public Duration getPrefetchPeriod() {
            return prefetchPeriod;
        }

        public void setPrefetchPeriod(Duration prefetchPeriod) {
            this.prefetchPeriod = prefetchPeriod;
        }

        public int getWorkerCorePoolSize() {
            return workerCorePoolSize;
        }

        public void setWorkerCorePoolSize(int workerCorePoolSize) {
            this.workerCorePoolSize = workerCorePoolSize;
        }
    }
}
