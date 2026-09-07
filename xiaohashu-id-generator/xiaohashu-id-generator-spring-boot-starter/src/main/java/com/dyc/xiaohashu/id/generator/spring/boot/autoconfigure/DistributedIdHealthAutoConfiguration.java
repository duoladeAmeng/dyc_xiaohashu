package com.dyc.xiaohashu.id.generator.spring.boot.autoconfigure;

import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;
import com.dyc.xiaohashu.id.generator.spring.boot.actuate.DistributedIdHealthIndicator;
import com.dyc.xiaohashu.id.generator.spring.boot.lifecycle.SnowflakeLeaseLifecycle;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * Actuator health auto-configuration for distributed ID generators.
 */
@AutoConfiguration(after = DistributedIdAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
public class DistributedIdHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "distributedIdHealthIndicator")
    public HealthIndicator distributedIdHealthIndicator(
            ObjectProvider<SnowflakeIdGenerator> snowflakeIdGenerators,
            ObjectProvider<SnowflakeLeaseLifecycle> snowflakeLeaseLifecycles,
            ObjectProvider<SegmentIdGenerator> segmentIdGenerators,
            ObjectProvider<SegmentChainIdGenerator> segmentChainIdGenerators,
            ObjectProvider<DataSource> dataSource,
            TimeService timeService
    ) {
        return new DistributedIdHealthIndicator(
                snowflakeIdGenerators.orderedStream().toList(),
                snowflakeLeaseLifecycles.orderedStream().toList(),
                segmentIdGenerators.orderedStream().toList(),
                segmentChainIdGenerators.orderedStream().toList(),
                Optional.ofNullable(dataSource.getIfAvailable()),
                timeService
        );
    }
}
