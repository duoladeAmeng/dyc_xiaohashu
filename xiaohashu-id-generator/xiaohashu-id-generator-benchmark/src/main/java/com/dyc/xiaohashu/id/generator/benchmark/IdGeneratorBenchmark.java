package com.dyc.xiaohashu.id.generator.benchmark;

import com.dyc.xiaohashu.id.generator.core.machine.InstanceIdentity;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.machine.MachineStatus;
import com.dyc.xiaohashu.id.generator.core.segment.DefaultSegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.DefaultSegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.DefaultSegmentPrefetchScheduler;
import com.dyc.xiaohashu.id.generator.core.segment.IdSegment;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentAllocator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainConfig;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdConfig;
import com.dyc.xiaohashu.id.generator.core.snowflake.DefaultSnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeConfig;
import com.dyc.xiaohashu.id.generator.core.time.SystemTimeService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMH benchmarks for core distributed ID generators.
 */
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(Threads.MAX)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class IdGeneratorBenchmark {

    @Benchmark
    public long snowflake(SnowflakeState state) {
        return state.generator.nextId();
    }

    @Benchmark
    public long segmentId(SegmentState state) {
        return state.generator.nextId();
    }

    @Benchmark
    public long segmentChainId(SegmentChainState state) {
        return state.generator.nextId();
    }

    @State(Scope.Benchmark)
    public static class SnowflakeState {

        private DefaultSnowflakeIdGenerator generator;

        @Setup(Level.Trial)
        public void setup() {
            Instant now = SystemTimeService.INSTANCE.now();
            MachineLease lease = new MachineLease(
                    "benchmark",
                    1,
                    new InstanceIdentity("jmh-snowflake", false),
                    MachineStatus.ACTIVE,
                    0,
                    now,
                    now.plus(Duration.ofHours(1)),
                    0
            );
            generator = new DefaultSnowflakeIdGenerator(SnowflakeConfig.defaults(), lease, SystemTimeService.INSTANCE);
        }
    }

    @State(Scope.Benchmark)
    public static class SegmentState {

        @Param({"10000", "100000"})
        private long step;

        private DefaultSegmentIdGenerator generator;

        @Setup(Level.Trial)
        public void setup() {
            generator = new DefaultSegmentIdGenerator(
                    "benchmark",
                    "segment",
                    new SegmentIdConfig(step),
                    new InMemorySegmentAllocator()
            );
        }
    }

    @State(Scope.Benchmark)
    public static class SegmentChainState {

        @Param({"10000", "100000"})
        private long step;

        @Param({"2", "8"})
        private int safeDistance;

        private DefaultSegmentPrefetchScheduler scheduler;
        private DefaultSegmentChainIdGenerator generator;

        @Setup(Level.Trial)
        public void setup() {
            scheduler = new DefaultSegmentPrefetchScheduler(Duration.ofSeconds(1), Math.max(1, Runtime.getRuntime().availableProcessors()));
            generator = new DefaultSegmentChainIdGenerator(
                    "benchmark",
                    "segment-chain",
                    new SegmentIdConfig(step),
                    new SegmentChainConfig(safeDistance, 1024, Duration.ofSeconds(1), 3, Duration.ofMillis(1)),
                    new InMemorySegmentAllocator(),
                    scheduler
            );
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (generator != null) {
                generator.close();
            }
            if (scheduler != null) {
                scheduler.close();
            }
        }
    }

    private static final class InMemorySegmentAllocator implements SegmentAllocator {

        private final AtomicLong maxId = new AtomicLong();

        @Override
        public synchronized IdSegment nextSegment(String namespace, String tag, long requestedStep) {
            long endInclusive = maxId.addAndGet(requestedStep);
            return new IdSegment(namespace, tag, endInclusive - requestedStep + 1, endInclusive);
        }
    }
}
