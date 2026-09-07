package com.dyc.xiaohashu.id.generator.spring.boot.properties;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;
import com.dyc.xiaohashu.id.generator.core.machine.InstanceIdentity;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLeaseConfig;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainConfig;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdConfig;
import com.dyc.xiaohashu.id.generator.core.snowflake.ClockBackwardsPolicy;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;

/**
 * Configuration properties for distributed ID generators.
 */
@ConfigurationProperties(prefix = "distributed-id")
public class DistributedIdProperties {

    private String namespace = "xiaohashu";
    private final Jdbc jdbc = new Jdbc();
    private final Snowflake snowflake = new Snowflake();
    private final Machine machine = new Machine();
    private final Segment segment = new Segment();
    private final SegmentChain segmentChain = new SegmentChain();

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Jdbc getJdbc() {
        return jdbc;
    }

    public Snowflake getSnowflake() {
        return snowflake;
    }

    public Machine getMachine() {
        return machine;
    }

    public Segment getSegment() {
        return segment;
    }

    public SegmentChain getSegmentChain() {
        return segmentChain;
    }

    public SnowflakeConfig toSnowflakeConfig() {
        long sequenceResetThreshold = snowflake.sequenceResetThreshold == null
                ? SnowflakeConfig.defaultSequenceResetThreshold(snowflake.sequenceBits)
                : snowflake.sequenceResetThreshold;
        return new SnowflakeConfig(
                snowflake.epoch,
                snowflake.timestampBits,
                snowflake.machineBits,
                snowflake.sequenceBits,
                sequenceResetThreshold,
                new ClockBackwardsPolicy(snowflake.clockBackwards.spinThreshold, snowflake.clockBackwards.maxWait)
        );
    }

    public MachineLeaseConfig toMachineLeaseConfig() {
        return new MachineLeaseConfig(machine.heartbeatInterval, machine.leaseTimeout, machine.maxHeartbeatFailures);
    }

    public InstanceIdentity toInstanceIdentity() {
        String configuredInstanceId = trimToNull(machine.instanceId);
        if (configuredInstanceId != null) {
            return new InstanceIdentity(configuredInstanceId, machine.stable);
        }
        String hostName = hostName();
        String instanceId = machine.stable ? hostName : hostName + ":" + ManagementFactory.getRuntimeMXBean().getName();
        return new InstanceIdentity(instanceId, machine.stable);
    }

    public SegmentIdConfig toSegmentIdConfig() {
        return new SegmentIdConfig(segment.defaultStep);
    }

    public SegmentIdConfig toSegmentChainSegmentIdConfig() {
        return new SegmentIdConfig(segmentChain.defaultStep);
    }

    public SegmentChainConfig toSegmentChainConfig() {
        return new SegmentChainConfig(
                segmentChain.safeDistance,
                segmentChain.maxPrefetchDistance,
                segmentChain.prefetchPeriod,
                segmentChain.prefetchRetryCount,
                segmentChain.prefetchRetryBackoff
        );
    }

    public void validateCommon() {
        requireText(namespace, "namespace");
        if (machine.shutdownTimeout == null || machine.shutdownTimeout.isNegative()) {
            throw new InvalidIdGeneratorConfigurationException("machine.shutdownTimeout must not be negative");
        }
        if (segmentChain.schedulerPoolSize <= 0) {
            throw new InvalidIdGeneratorConfigurationException("segmentChain.schedulerPoolSize must be greater than zero");
        }
        if (segmentChain.shutdownTimeout == null || segmentChain.shutdownTimeout.isNegative()) {
            throw new InvalidIdGeneratorConfigurationException("segmentChain.shutdownTimeout must not be negative");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "unknown-host";
        }
    }

    private static void requireText(String value, String name) {
        if (trimToNull(value) == null) {
            throw new InvalidIdGeneratorConfigurationException(name + " must not be blank");
        }
    }

    public static class Jdbc {

        private boolean initializeSchema;

        public boolean isInitializeSchema() {
            return initializeSchema;
        }

        public void setInitializeSchema(boolean initializeSchema) {
            this.initializeSchema = initializeSchema;
        }
    }

    public static class Snowflake {

        private boolean enabled;
        private Instant epoch = SnowflakeConfig.DEFAULT_EPOCH;
        private int timestampBits = SnowflakeConfig.DEFAULT_TIMESTAMP_BITS;
        private int machineBits = SnowflakeConfig.DEFAULT_MACHINE_BITS;
        private int sequenceBits = SnowflakeConfig.DEFAULT_SEQUENCE_BITS;
        private Long sequenceResetThreshold;
        private final ClockBackwards clockBackwards = new ClockBackwards();

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

        public int getTimestampBits() {
            return timestampBits;
        }

        public void setTimestampBits(int timestampBits) {
            this.timestampBits = timestampBits;
        }

        public int getMachineBits() {
            return machineBits;
        }

        public void setMachineBits(int machineBits) {
            this.machineBits = machineBits;
        }

        public int getSequenceBits() {
            return sequenceBits;
        }

        public void setSequenceBits(int sequenceBits) {
            this.sequenceBits = sequenceBits;
        }

        public Long getSequenceResetThreshold() {
            return sequenceResetThreshold;
        }

        public void setSequenceResetThreshold(Long sequenceResetThreshold) {
            this.sequenceResetThreshold = sequenceResetThreshold;
        }

        public ClockBackwards getClockBackwards() {
            return clockBackwards;
        }
    }

    public static class ClockBackwards {

        private Duration spinThreshold = ClockBackwardsPolicy.DEFAULT.spinThreshold();
        private Duration maxWait = ClockBackwardsPolicy.DEFAULT.maxWait();

        public Duration getSpinThreshold() {
            return spinThreshold;
        }

        public void setSpinThreshold(Duration spinThreshold) {
            this.spinThreshold = spinThreshold;
        }

        public Duration getMaxWait() {
            return maxWait;
        }

        public void setMaxWait(Duration maxWait) {
            this.maxWait = maxWait;
        }
    }

    public static class Machine {

        private String allocator = "jdbc";
        private String instanceId;
        private boolean stable;
        private Duration heartbeatInterval = MachineLeaseConfig.DEFAULT_HEARTBEAT_INTERVAL;
        private Duration leaseTimeout = MachineLeaseConfig.DEFAULT_LEASE_TIMEOUT;
        private Duration shutdownTimeout = Duration.ofSeconds(5);
        private int maxHeartbeatFailures = MachineLeaseConfig.DEFAULT_MAX_HEARTBEAT_FAILURES;

        public String getAllocator() {
            return allocator;
        }

        public void setAllocator(String allocator) {
            this.allocator = allocator;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        public boolean isStable() {
            return stable;
        }

        public void setStable(boolean stable) {
            this.stable = stable;
        }

        public Duration getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        public Duration getLeaseTimeout() {
            return leaseTimeout;
        }

        public void setLeaseTimeout(Duration leaseTimeout) {
            this.leaseTimeout = leaseTimeout;
        }

        public int getMaxHeartbeatFailures() {
            return maxHeartbeatFailures;
        }

        public void setMaxHeartbeatFailures(int maxHeartbeatFailures) {
            this.maxHeartbeatFailures = maxHeartbeatFailures;
        }

        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        public void setShutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }
    }

    public static class Segment {

        private boolean enabled;
        private String tag = "default";
        private long defaultStep = SegmentIdConfig.DEFAULT_STEP;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public long getDefaultStep() {
            return defaultStep;
        }

        public void setDefaultStep(long defaultStep) {
            this.defaultStep = defaultStep;
        }
    }

    public static class SegmentChain {

        private boolean enabled;
        private String tag = "default-chain";
        private long defaultStep = SegmentIdConfig.DEFAULT_STEP;
        private int safeDistance = SegmentChainConfig.DEFAULT_SAFE_DISTANCE;
        private int maxPrefetchDistance = SegmentChainConfig.DEFAULT_MAX_PREFETCH_DISTANCE;
        private Duration prefetchPeriod = SegmentChainConfig.DEFAULT_PREFETCH_PERIOD;
        private int prefetchRetryCount = SegmentChainConfig.DEFAULT_PREFETCH_RETRY_COUNT;
        private Duration prefetchRetryBackoff = SegmentChainConfig.DEFAULT_PREFETCH_RETRY_BACKOFF;
        private int schedulerPoolSize = Math.max(1, Runtime.getRuntime().availableProcessors());
        private Duration shutdownTimeout = Duration.ofSeconds(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public long getDefaultStep() {
            return defaultStep;
        }

        public void setDefaultStep(long defaultStep) {
            this.defaultStep = defaultStep;
        }

        public int getSafeDistance() {
            return safeDistance;
        }

        public void setSafeDistance(int safeDistance) {
            this.safeDistance = safeDistance;
        }

        public int getMaxPrefetchDistance() {
            return maxPrefetchDistance;
        }

        public void setMaxPrefetchDistance(int maxPrefetchDistance) {
            this.maxPrefetchDistance = maxPrefetchDistance;
        }

        public Duration getPrefetchPeriod() {
            return prefetchPeriod;
        }

        public void setPrefetchPeriod(Duration prefetchPeriod) {
            this.prefetchPeriod = prefetchPeriod;
        }

        public int getPrefetchRetryCount() {
            return prefetchRetryCount;
        }

        public void setPrefetchRetryCount(int prefetchRetryCount) {
            this.prefetchRetryCount = prefetchRetryCount;
        }

        public Duration getPrefetchRetryBackoff() {
            return prefetchRetryBackoff;
        }

        public void setPrefetchRetryBackoff(Duration prefetchRetryBackoff) {
            this.prefetchRetryBackoff = prefetchRetryBackoff;
        }

        public int getSchedulerPoolSize() {
            return schedulerPoolSize;
        }

        public void setSchedulerPoolSize(int schedulerPoolSize) {
            this.schedulerPoolSize = schedulerPoolSize;
        }

        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        public void setShutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }
    }
}
