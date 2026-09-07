package com.dyc.config;

import com.dyc.xiaohashu.id.generator.core.machine.DefaultClockBackwardsSynchronizer;
import com.dyc.xiaohashu.id.generator.core.machine.InstanceId;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.concurrent.PrefetchWorkerExecutorService;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcIdGeneratorSchemaInitializer;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcMachineIdAllocator;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcRetryExecutor;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcSegmentAllocator;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
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
    public JdbcRetryExecutor jdbcRetryExecutor() {
        DistributedIdProperties.Jdbc jdbc = properties.getJdbc();
        return new JdbcRetryExecutor(jdbc.getRetryAttempts(), jdbc.getInitialBackoff(), jdbc.getMaxBackoff());
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcMachineIdAllocator jdbcMachineIdAllocator(DataSource dataSource, JdbcRetryExecutor retryExecutor) {
        return new JdbcMachineIdAllocator(dataSource, retryExecutor);
    }

    @Bean
    @ConditionalOnMissingBean(name = "segmentJdbcAllocator")
    @DependsOn("jdbcIdGeneratorSchemaInitializer")
    public JdbcSegmentAllocator segmentJdbcAllocator(DataSource dataSource, JdbcRetryExecutor retryExecutor) {
        return new JdbcSegmentAllocator(properties.getNamespace(), properties.getSegment().getName(), properties.getSegment().getStep(), dataSource, retryExecutor);
    }

    @Bean
    @ConditionalOnMissingBean(name = "segmentChainJdbcAllocator")
    @DependsOn("jdbcIdGeneratorSchemaInitializer")
    public JdbcSegmentAllocator segmentChainJdbcAllocator(DataSource dataSource, JdbcRetryExecutor retryExecutor) {
        return new JdbcSegmentAllocator(properties.getNamespace(), properties.getSegmentChain().getName(), properties.getSegmentChain().getStep(), dataSource, retryExecutor);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public PrefetchWorkerExecutorService prefetchWorkerExecutorService() {
        return new PrefetchWorkerExecutorService(properties.getSegmentChain().getPrefetchPeriod(), properties.getSegmentChain().getWorkerCorePoolSize());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @DependsOn("jdbcIdGeneratorSchemaInitializer")
    public SnowflakeIdGenerator snowflakeIdGenerator(JdbcMachineIdAllocator machineIdAllocator, ObjectProvider<MeterRegistry> meterRegistry) {
        DistributedIdProperties.Snowflake snowflake = properties.getSnowflake();
        return new SnowflakeIdGenerator(
                properties.getNamespace(),
                snowflake.getEpoch().toEpochMilli(),
                snowflake.getTimestampBit(),
                snowflake.getMachineBit(),
                snowflake.getSequenceBit(),
                InstanceId.of(resolveInstanceId(), properties.isStableInstance()),
                machineIdAllocator,
                snowflake.getHeartbeatInterval(),
                snowflake.getSafeGuardDuration(),
                new DefaultClockBackwardsSynchronizer(snowflake.getClockSpinThreshold(), snowflake.getClockBrokenThreshold()),
                meterRegistry.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public SegmentIdGenerator segmentIdGenerator(@Qualifier("segmentJdbcAllocator") JdbcSegmentAllocator segmentJdbcAllocator, ObjectProvider<MeterRegistry> meterRegistry) {
        return new SegmentIdGenerator(properties.getSegment().getTtlSeconds(), segmentJdbcAllocator, meterRegistry.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public SegmentChainIdGenerator segmentChainIdGenerator(@Qualifier("segmentChainJdbcAllocator") JdbcSegmentAllocator segmentChainJdbcAllocator,
                                                           PrefetchWorkerExecutorService prefetchWorkerExecutorService,
                                                           ObjectProvider<MeterRegistry> meterRegistry) {
        return new SegmentChainIdGenerator(
                properties.getSegmentChain().getTtlSeconds(),
                properties.getSegmentChain().getSafeDistance(),
                segmentChainJdbcAllocator,
                prefetchWorkerExecutorService,
                meterRegistry.getIfAvailable()
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
