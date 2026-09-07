package com.dyc.xiaohashu.id.generator.spring.boot.actuate;

import com.dyc.xiaohashu.id.generator.core.machine.InstanceIdentity;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.machine.MachineStatus;
import com.dyc.xiaohashu.id.generator.core.segment.DefaultSegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.IdSegment;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdConfig;
import com.dyc.xiaohashu.id.generator.core.snowflake.DefaultSnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeConfig;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedIdHealthIndicatorTest {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");

    @Test
    void healthShouldBeUpWhenGeneratorsCanServeIds() {
        TimeService timeService = () -> NOW;
        DefaultSnowflakeIdGenerator snowflake = new DefaultSnowflakeIdGenerator(
                SnowflakeConfig.defaults(),
                lease(MachineStatus.ACTIVE, NOW.plusSeconds(30)),
                timeService
        );
        DefaultSegmentIdGenerator segment = new DefaultSegmentIdGenerator(
                "xiaohashu",
                "note",
                new SegmentIdConfig(10),
                new InMemorySegmentAllocator()
        );

        Health health = new DistributedIdHealthIndicator(
                List.of(snowflake),
                List.of(),
                List.of(segment),
                List.of(),
                Optional.empty(),
                timeService
        ).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKeys("snowflake", "segment", "segmentChain");
    }

    @Test
    void healthShouldBeOutOfServiceWhenSnowflakeLeaseCannotGenerate() {
        TimeService timeService = () -> NOW;
        DefaultSnowflakeIdGenerator snowflake = new DefaultSnowflakeIdGenerator(
                SnowflakeConfig.defaults(),
                lease(MachineStatus.EXPIRED, NOW.minusSeconds(1)),
                timeService
        );

        Health health = new DistributedIdHealthIndicator(
                List.of(snowflake),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                timeService
        ).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsKey("problems");
    }

    private static MachineLease lease(MachineStatus status, Instant leaseExpiresAt) {
        return new MachineLease(
                "xiaohashu",
                1,
                new InstanceIdentity("test-instance", false),
                status,
                0,
                NOW,
                leaseExpiresAt,
                0
        );
    }

    private static final class InMemorySegmentAllocator implements com.dyc.xiaohashu.id.generator.core.segment.SegmentAllocator {

        private final AtomicLong maxId = new AtomicLong();

        @Override
        public IdSegment nextSegment(String namespace, String tag, long requestedStep) {
            long end = maxId.addAndGet(requestedStep);
            return new IdSegment(namespace, tag, end - requestedStep + 1, end);
        }
    }
}
