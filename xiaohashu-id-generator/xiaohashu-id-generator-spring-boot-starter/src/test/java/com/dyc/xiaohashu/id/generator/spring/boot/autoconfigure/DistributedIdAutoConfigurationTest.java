package com.dyc.xiaohashu.id.generator.spring.boot.autoconfigure;

import com.dyc.xiaohashu.id.generator.core.machine.MachineIdAllocator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentAllocator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.spring.boot.properties.DistributedIdProperties;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedIdAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DistributedIdAutoConfiguration.class,
                    DistributedIdMetricsAutoConfiguration.class,
                    DistributedIdHealthAutoConfiguration.class
            ));

    @Test
    void contextShouldStartWithOnlyInfrastructureBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DistributedIdProperties.class);
            assertThat(context).doesNotHaveBean(SnowflakeIdGenerator.class);
            assertThat(context).doesNotHaveBean(SegmentIdGenerator.class);
            assertThat(context).doesNotHaveBean(SegmentChainIdGenerator.class);
            assertThat(context).hasSingleBean(MeterBinder.class);
            assertThat(context).hasSingleBean(HealthIndicator.class);
        });
    }

    @Test
    void contextShouldCreateEnabledGeneratorsWithJdbcAllocators() {
        contextRunner
                .withUserConfiguration(H2DataSourceConfiguration.class)
                .withPropertyValues(
                        "distributed-id.jdbc.initialize-schema=true",
                        "distributed-id.namespace=xiaohashu",
                        "distributed-id.snowflake.enabled=true",
                        "distributed-id.machine.instance-id=test-instance",
                        "distributed-id.segment.enabled=true",
                        "distributed-id.segment.tag=note",
                        "distributed-id.segment.default-step=5",
                        "distributed-id.segment-chain.enabled=true",
                        "distributed-id.segment-chain.tag=chain",
                        "distributed-id.segment-chain.default-step=5",
                        "distributed-id.segment-chain.safe-distance=1",
                        "distributed-id.segment-chain.max-prefetch-distance=4",
                        "distributed-id.segment-chain.scheduler-pool-size=1"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MachineIdAllocator.class);
                    assertThat(context).hasSingleBean(SegmentAllocator.class);
                    assertThat(context).hasSingleBean(SnowflakeIdGenerator.class);
                    assertThat(context).hasBean("segmentIdGenerator");
                    assertThat(context).hasSingleBean(SegmentChainIdGenerator.class);
                    assertThat(context).hasSingleBean(MeterBinder.class);
                    assertThat(context).hasSingleBean(HealthIndicator.class);

                    assertThat(context.getBean(SnowflakeIdGenerator.class).nextId()).isPositive();
                    assertThat(((SegmentIdGenerator) context.getBean("segmentIdGenerator")).nextId()).isEqualTo(1L);
                    assertThat(context.getBean(SegmentChainIdGenerator.class).nextId()).isEqualTo(1L);
                });
    }

    @Test
    void contextShouldFailWhenSnowflakeEpochIsInvalid() {
        contextRunner
                .withUserConfiguration(H2DataSourceConfiguration.class)
                .withPropertyValues(
                        "distributed-id.jdbc.initialize-schema=true",
                        "distributed-id.snowflake.enabled=true",
                        "distributed-id.snowflake.epoch=2999-01-01T00:00:00Z",
                        "distributed-id.machine.instance-id=test-instance"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextShouldFailWhenLeaseTimeoutIsNotGreaterThanHeartbeatInterval() {
        contextRunner
                .withPropertyValues(
                        "distributed-id.machine.heartbeat-interval=30s",
                        "distributed-id.machine.lease-timeout=10s"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    static class H2DataSourceConfiguration {

        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
            return dataSource;
        }
    }
}
