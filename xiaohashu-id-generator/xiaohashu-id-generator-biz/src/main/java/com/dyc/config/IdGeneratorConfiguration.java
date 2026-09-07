package com.dyc.config;

import com.dyc.xiaohashu.id.generator.core.machine.DefaultClockBackwardsSynchronizer;
import com.dyc.xiaohashu.id.generator.core.machine.DefaultMachineIdGuarder;
import com.dyc.xiaohashu.id.generator.core.machine.GuardDistribute;
import com.dyc.xiaohashu.id.generator.core.machine.InstanceId;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdGuarder;
import com.dyc.xiaohashu.id.generator.core.machine.MachineState;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.IdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.ClockSyncSnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.concurrent.PrefetchWorkerExecutorService;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcIdGeneratorSchemaInitializer;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcMachineIdAllocator;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcSegmentAllocator;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties(DistributedIdProperties.class)
public class IdGeneratorConfiguration {

    private final DistributedIdProperties properties;

    public IdGeneratorConfiguration(DistributedIdProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        properties.validate();
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultClockBackwardsSynchronizer defaultClockBackwardsSynchronizer() {
        DistributedIdProperties.Snowflake snowflake = properties.getSnowflake();
        return new DefaultClockBackwardsSynchronizer(snowflake.getClockSpinThreshold(), snowflake.getClockBrokenThreshold());
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcMachineIdAllocator jdbcMachineIdAllocator(DataSource dataSource, DefaultClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        return new JdbcMachineIdAllocator(dataSource, com.dyc.xiaohashu.id.generator.core.machine.MachineStateStorage.LOCAL, clockBackwardsSynchronizer);
    }

    @Bean
    @ConditionalOnMissingBean
    public MachineIdGuarder machineIdGuarder(JdbcMachineIdAllocator machineIdAllocator) {
        DistributedIdProperties.Snowflake snowflake = properties.getSnowflake();
        return new DefaultMachineIdGuarder(
                machineIdAllocator,
                DefaultMachineIdGuarder.executorService(),
                snowflake.getGuarderInitialDelay(),
                snowflake.getGuarderDelay(),
                snowflake.getSafeGuardDuration()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public GuardDistribute guardDistribute(JdbcMachineIdAllocator machineIdAllocator, MachineIdGuarder machineIdGuarder) {
        return new GuardDistribute(machineIdAllocator, machineIdGuarder);
    }

    @Bean
    @ConditionalOnMissingBean
    public CosIdMachineIdLifecycle cosIdMachineIdLifecycle(MachineIdGuarder machineIdGuarder, JdbcMachineIdAllocator machineIdAllocator) {
        return new CosIdMachineIdLifecycle(machineIdGuarder, machineIdAllocator);
    }

    @Bean
    @ConditionalOnMissingBean(name = "segmentJdbcAllocator")
    @DependsOn("jdbcIdGeneratorSchemaInitializer")
    public JdbcSegmentAllocator segmentJdbcAllocator(DataSource dataSource) {
        return new JdbcSegmentAllocator(properties.getNamespace(), properties.getSegment().getName(), properties.getSegment().getStep(), dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(name = "segmentChainJdbcAllocator")
    @DependsOn("jdbcIdGeneratorSchemaInitializer")
    public JdbcSegmentAllocator segmentChainJdbcAllocator(DataSource dataSource) {
        return new JdbcSegmentAllocator(properties.getNamespace(), properties.getSegmentChain().getName(), properties.getSegmentChain().getStep(), dataSource);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public PrefetchWorkerExecutorService prefetchWorkerExecutorService() {
        return new PrefetchWorkerExecutorService(properties.getSegmentChain().getPrefetchPeriod(), properties.getSegmentChain().getWorkerCorePoolSize());
    }

    @Bean(name = "snowflakeIdGenerator")
    @ConditionalOnMissingBean(name = "snowflakeIdGenerator")
    @DependsOn("jdbcIdGeneratorSchemaInitializer")
    public IdGenerator snowflakeIdGenerator(GuardDistribute guardDistribute, DefaultClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        DistributedIdProperties.Snowflake snowflake = properties.getSnowflake();
        InstanceId instanceId = InstanceId.of(resolveInstanceId(), properties.isStableInstance());
        MachineState machineState = guardDistribute.distribute(properties.getNamespace(), snowflake.getMachineBit(), instanceId, snowflake.getSafeGuardDuration());
        SnowflakeIdGenerator snowflakeIdGenerator = new SnowflakeIdGenerator(
                snowflake.getEpoch().toEpochMilli(),
                snowflake.getTimestampBit(),
                snowflake.getMachineBit(),
                snowflake.getSequenceBit(),
                machineState.getMachineId(),
                snowflake.getSequenceResetThreshold()
        );
        if (snowflake.isClockSync()) {
            return new ClockSyncSnowflakeIdGenerator(snowflakeIdGenerator, clockBackwardsSynchronizer);
        }
        return snowflakeIdGenerator;
    }

    @Bean
    @ConditionalOnMissingBean
    public SegmentIdGenerator segmentIdGenerator(@Qualifier("segmentJdbcAllocator") JdbcSegmentAllocator segmentJdbcAllocator) {
        return new SegmentIdGenerator(properties.getSegment().getTtlSeconds(), segmentJdbcAllocator);
    }

    @Bean
    @ConditionalOnMissingBean
    public SegmentChainIdGenerator segmentChainIdGenerator(@Qualifier("segmentChainJdbcAllocator") JdbcSegmentAllocator segmentChainJdbcAllocator,
                                                           PrefetchWorkerExecutorService prefetchWorkerExecutorService) {
        return new SegmentChainIdGenerator(
                properties.getSegmentChain().getTtlSeconds(),
                properties.getSegmentChain().getSafeDistance(),
                segmentChainJdbcAllocator,
                prefetchWorkerExecutorService
        );
    }

    @Bean
    public JdbcIdGeneratorSchemaInitializer jdbcIdGeneratorSchemaInitializer(DataSource dataSource) {
        JdbcIdGeneratorSchemaInitializer initializer = new JdbcIdGeneratorSchemaInitializer(dataSource);
        if (properties.getJdbc().isInitializeSchema()) {
            initializer.initialize(
                    properties.getNamespace(),
                    properties.getSegment().getName(),
                    properties.getSegment().getStep(),
                    properties.getSegmentChain().getName(),
                    properties.getSegmentChain().getStep(),
                    0
            );
        }
        return initializer;
    }

    private String resolveInstanceId() {
        if (properties.getInstanceId() != null && !properties.getInstanceId().isBlank()) {
            return properties.getInstanceId();
        }
        try {
            return InetAddress.getLocalHost().getHostAddress() + ":" + ManagementFactory.getRuntimeMXBean().getName();
        } catch (UnknownHostException ignored) {
            return UUID.randomUUID().toString();
        }
    }
}
