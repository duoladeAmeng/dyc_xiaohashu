package com.dyc.xiaohashu.id.generator.spring.boot.autoconfigure;

import com.dyc.xiaohashu.id.generator.core.machine.InstanceIdentity;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdAllocator;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLeaseConfig;
import com.dyc.xiaohashu.id.generator.core.segment.DefaultSegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.DefaultSegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.DefaultSegmentPrefetchScheduler;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentAllocator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentPrefetchScheduler;
import com.dyc.xiaohashu.id.generator.core.snowflake.DefaultSnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeConfig;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.core.time.SystemTimeService;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcMachineIdAllocator;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcRetryConfig;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcRetryTemplate;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcSchemaInitializer;
import com.dyc.xiaohashu.id.generator.jdbc.JdbcSegmentAllocator;
import com.dyc.xiaohashu.id.generator.spring.boot.lifecycle.SnowflakeLeaseLifecycle;
import com.dyc.xiaohashu.id.generator.spring.boot.properties.DistributedIdProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Spring Boot auto-configuration for distributed ID generators.
 */
@AutoConfiguration
@EnableConfigurationProperties(DistributedIdProperties.class)
public class DistributedIdAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TimeService distributedIdTimeService() {
        return SystemTimeService.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    public MachineLeaseConfig distributedIdMachineLeaseConfig(DistributedIdProperties properties) {
        properties.validateCommon();
        return properties.toMachineLeaseConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcRetryTemplate distributedIdJdbcRetryTemplate() {
        return new JdbcRetryTemplate(JdbcRetryConfig.defaults());
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "distributed-id.jdbc", name = "initialize-schema", havingValue = "true")
    public JdbcSchemaInitializer distributedIdJdbcSchemaInitializer(DataSource dataSource) {
        return new JdbcSchemaInitializer(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(MachineIdAllocator.class)
    @ConditionalOnProperty(prefix = "distributed-id.machine", name = "allocator", havingValue = "jdbc", matchIfMissing = true)
    public MachineIdAllocator distributedIdMachineIdAllocator(
            DataSource dataSource,
            MachineLeaseConfig leaseConfig,
            TimeService timeService,
            JdbcRetryTemplate retryTemplate,
            ObjectProvider<JdbcSchemaInitializer> schemaInitializerProvider
    ) {
        schemaInitializerProvider.ifAvailable(JdbcSchemaInitializer::initialize);
        return new JdbcMachineIdAllocator(dataSource, leaseConfig, timeService, retryTemplate);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(SegmentAllocator.class)
    public SegmentAllocator distributedIdSegmentAllocator(
            DataSource dataSource,
            TimeService timeService,
            JdbcRetryTemplate retryTemplate,
            ObjectProvider<JdbcSchemaInitializer> schemaInitializerProvider
    ) {
        schemaInitializerProvider.ifAvailable(JdbcSchemaInitializer::initialize);
        return new JdbcSegmentAllocator(dataSource, timeService, retryTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "distributed-id.snowflake", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(SnowflakeIdGenerator.class)
    public DefaultSnowflakeIdGenerator snowflakeIdGenerator(
            DistributedIdProperties properties,
            MachineIdAllocator machineIdAllocator,
            TimeService timeService
    ) {
        properties.validateCommon();
        SnowflakeConfig snowflakeConfig = properties.toSnowflakeConfig();
        snowflakeConfig.validateUsableAt(timeService.now());
        InstanceIdentity instanceIdentity = properties.toInstanceIdentity();
        MachineLease lease = machineIdAllocator.acquire(
                properties.getNamespace(),
                instanceIdentity,
                snowflakeConfig.maxMachineId(),
                0
        );
        return new DefaultSnowflakeIdGenerator(snowflakeConfig, lease, timeService);
    }

    @Bean
    @ConditionalOnBean(DefaultSnowflakeIdGenerator.class)
    @ConditionalOnMissingBean
    public SnowflakeLeaseLifecycle snowflakeLeaseLifecycle(
            DistributedIdProperties properties,
            DefaultSnowflakeIdGenerator snowflakeIdGenerator,
            MachineIdAllocator machineIdAllocator,
            MachineLeaseConfig leaseConfig
    ) {
        return new SnowflakeLeaseLifecycle(
                snowflakeIdGenerator,
                machineIdAllocator,
                leaseConfig,
                properties.getMachine().getShutdownTimeout()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "distributed-id.segment", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "segmentIdGenerator")
    public SegmentIdGenerator segmentIdGenerator(DistributedIdProperties properties, SegmentAllocator segmentAllocator) {
        properties.validateCommon();
        return new DefaultSegmentIdGenerator(
                properties.getNamespace(),
                properties.getSegment().getTag(),
                properties.toSegmentIdConfig(),
                segmentAllocator
        );
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "distributed-id.segment-chain", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(SegmentPrefetchScheduler.class)
    public SegmentPrefetchScheduler segmentPrefetchScheduler(DistributedIdProperties properties) {
        properties.validateCommon();
        return new DefaultSegmentPrefetchScheduler(
                properties.getSegmentChain().getPrefetchPeriod(),
                properties.getSegmentChain().getSchedulerPoolSize(),
                properties.getSegmentChain().getShutdownTimeout()
        );
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "distributed-id.segment-chain", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(SegmentChainIdGenerator.class)
    public DefaultSegmentChainIdGenerator segmentChainIdGenerator(
            DistributedIdProperties properties,
            SegmentAllocator segmentAllocator,
            SegmentPrefetchScheduler segmentPrefetchScheduler
    ) {
        properties.validateCommon();
        return new DefaultSegmentChainIdGenerator(
                properties.getNamespace(),
                properties.getSegmentChain().getTag(),
                properties.toSegmentChainSegmentIdConfig(),
                properties.toSegmentChainConfig(),
                segmentAllocator,
                segmentPrefetchScheduler
        );
    }
}
