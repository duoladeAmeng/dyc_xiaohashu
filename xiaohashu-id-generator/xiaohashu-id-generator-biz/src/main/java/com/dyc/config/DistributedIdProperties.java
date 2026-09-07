package com.dyc.config;

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
        if (snowflake.heartbeatInterval.compareTo(snowflake.safeGuardDuration) >= 0) {
            throw new IllegalArgumentException("snowflake heartbeatInterval must be less than safeGuardDuration.");
        }
        if (jdbc.initialBackoff.compareTo(jdbc.maxBackoff) > 0) {
            throw new IllegalArgumentException("jdbc initialBackoff must be less than or equal to maxBackoff.");
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
        @Min(1)
        private int retryAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(20);
        private Duration maxBackoff = Duration.ofMillis(500);

        public boolean isInitializeSchema() {
            return initializeSchema;
        }

        public void setInitializeSchema(boolean initializeSchema) {
            this.initializeSchema = initializeSchema;
        }

        public int getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }

    public static class Snowflake {
        private boolean enabled = true;
        private Instant epoch = Instant.parse("2025-01-01T00:00:00Z");
        @Min(1)
        private int timestampBit = 41;
        @Min(1)
        private int machineBit = 10;
        @Min(1)
        private int sequenceBit = 12;
        private Duration heartbeatInterval = Duration.ofSeconds(10);
        private Duration safeGuardDuration = Duration.ofSeconds(30);
        @Min(1)
        private int clockSpinThreshold = 1;
        @Min(2)
        private int clockBrokenThreshold = 500;

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

        public Duration getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
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
