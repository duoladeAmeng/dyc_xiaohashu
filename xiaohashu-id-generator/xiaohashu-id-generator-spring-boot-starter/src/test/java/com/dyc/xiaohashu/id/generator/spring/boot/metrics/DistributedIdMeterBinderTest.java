package com.dyc.xiaohashu.id.generator.spring.boot.metrics;

import com.dyc.xiaohashu.id.generator.core.machine.InstanceIdentity;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.machine.MachineStatus;
import com.dyc.xiaohashu.id.generator.core.segment.DefaultSegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.IdSegment;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdConfig;
import com.dyc.xiaohashu.id.generator.core.snowflake.DefaultSnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeConfig;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedIdMeterBinderTest {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");

    @Test
    void bindToShouldExposeSnowflakeAndSegmentMetrics() {
        TimeService timeService = () -> NOW;
        DefaultSnowflakeIdGenerator snowflake = new DefaultSnowflakeIdGenerator(
                SnowflakeConfig.defaults(),
                activeLease(),
                timeService
        );
        DefaultSegmentIdGenerator segment = new DefaultSegmentIdGenerator(
                "xiaohashu",
                "note",
                new SegmentIdConfig(10),
                new InMemorySegmentAllocator()
        );
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new DistributedIdMeterBinder(
                List.of(snowflake),
                List.of(),
                List.of(segment),
                List.of(),
                List.of()
        ).bindTo(registry);

        snowflake.nextId();
        segment.nextId();

        assertThat(registry.get("xiaohashu.id.snowflake.generated").functionCounter().count()).isEqualTo(1);
        assertThat(registry.get("xiaohashu.id.snowflake.machine.id").gauge().value()).isEqualTo(1);
        assertThat(registry.get("xiaohashu.id.segment.generated").tag("tag", "note").functionCounter().count()).isEqualTo(1);
        assertThat(registry.get("xiaohashu.id.segment.current.remaining").tag("tag", "note").gauge().value()).isEqualTo(9);
    }

    private static MachineLease activeLease() {
        return new MachineLease(
                "xiaohashu",
                1,
                new InstanceIdentity("test-instance", false),
                MachineStatus.ACTIVE,
                0,
                NOW,
                NOW.plusSeconds(30),
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
