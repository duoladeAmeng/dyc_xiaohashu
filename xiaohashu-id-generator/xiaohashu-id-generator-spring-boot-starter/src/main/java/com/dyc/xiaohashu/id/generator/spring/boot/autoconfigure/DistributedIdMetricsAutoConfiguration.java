package com.dyc.xiaohashu.id.generator.spring.boot.autoconfigure;

import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentPrefetchScheduler;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.spring.boot.lifecycle.SnowflakeLeaseLifecycle;
import com.dyc.xiaohashu.id.generator.spring.boot.metrics.DistributedIdMeterBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Micrometer auto-configuration for distributed ID generators.
 */
@AutoConfiguration(after = DistributedIdAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class DistributedIdMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "distributedIdMeterBinder")
    public MeterBinder distributedIdMeterBinder(
            ObjectProvider<SnowflakeIdGenerator> snowflakeIdGenerators,
            ObjectProvider<SnowflakeLeaseLifecycle> snowflakeLeaseLifecycles,
            ObjectProvider<SegmentIdGenerator> segmentIdGenerators,
            ObjectProvider<SegmentChainIdGenerator> segmentChainIdGenerators,
            ObjectProvider<SegmentPrefetchScheduler> segmentPrefetchSchedulers
    ) {
        return new DistributedIdMeterBinder(
                snowflakeIdGenerators.orderedStream().toList(),
                snowflakeLeaseLifecycles.orderedStream().toList(),
                segmentIdGenerators.orderedStream().toList(),
                segmentChainIdGenerators.orderedStream().toList(),
                segmentPrefetchSchedulers.orderedStream().toList()
        );
    }
}
