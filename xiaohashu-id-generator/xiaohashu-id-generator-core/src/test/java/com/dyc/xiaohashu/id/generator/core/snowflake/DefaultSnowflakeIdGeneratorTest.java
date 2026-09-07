package com.dyc.xiaohashu.id.generator.core.snowflake;

import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;
import com.dyc.xiaohashu.id.generator.core.machine.InstanceIdentity;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.machine.MachineStatus;
import com.dyc.xiaohashu.id.generator.core.time.MutableTimeService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSnowflakeIdGeneratorTest {

    private static final long NOW_MILLIS = Instant.parse("2026-09-06T00:00:00Z").toEpochMilli();

    @Test
    void nextIdShouldBeUniqueAndIncreasingWithinSameMillisecond() {
        MutableTimeService timeService = new MutableTimeService(NOW_MILLIS);
        DefaultSnowflakeIdGenerator generator = new DefaultSnowflakeIdGenerator(SnowflakeConfig.defaults(), activeLease(1, 0, NOW_MILLIS + 10_000), timeService);

        long first = generator.nextId();
        long second = generator.nextId();

        assertTrue(second > first);
        assertEquals(NOW_MILLIS, generator.lastTimestamp());
        assertEquals(2, generator.generatedTotal());
    }

    @Test
    void nextIdShouldWaitNextMillisecondWhenSequenceOverflows() {
        MutableTimeService timeService = new MutableTimeService(1_000L);
        SnowflakeConfig config = new SnowflakeConfig(
                Instant.EPOCH,
                41,
                2,
                2,
                1,
                new ClockBackwardsPolicy(Duration.ZERO, Duration.ofMillis(5))
        );
        DefaultSnowflakeIdGenerator generator = new DefaultSnowflakeIdGenerator(config, activeLease(1, 0, 10_000), timeService);
        timeService.script(1_000L, 1_000L, 1_000L, 1_000L, 1_000L, 1_001L);

        generator.nextId();
        generator.nextId();
        generator.nextId();
        generator.nextId();
        generator.nextId();

        assertEquals(1_001L, generator.lastTimestamp());
        assertEquals(1, generator.sequenceOverflowTotal());
    }

    @Test
    void nextIdShouldRejectExpiredLease() {
        MutableTimeService timeService = new MutableTimeService(NOW_MILLIS);
        DefaultSnowflakeIdGenerator generator = new DefaultSnowflakeIdGenerator(SnowflakeConfig.defaults(), activeLease(1, 0, NOW_MILLIS - 1), timeService);

        assertThrows(GeneratorUnavailableException.class, generator::nextId);
    }

    @Test
    void nextIdShouldRejectLargeClockRollback() {
        MutableTimeService timeService = new MutableTimeService(1_000L);
        SnowflakeConfig config = new SnowflakeConfig(
                Instant.EPOCH,
                41,
                10,
                12,
                SnowflakeConfig.defaultSequenceResetThreshold(12),
                new ClockBackwardsPolicy(Duration.ZERO, Duration.ofMillis(5))
        );
        DefaultSnowflakeIdGenerator generator = new DefaultSnowflakeIdGenerator(config, activeLease(1, 0, 10_000), timeService);
        generator.nextId();
        timeService.setMillis(900L);

        assertThrows(ClockBackwardsException.class, generator::nextId);
        assertEquals(1, generator.clockBackwardsTotal());
    }

    @Test
    void nextIdShouldBeUniqueWhenCalledConcurrently() throws InterruptedException {
        MutableTimeService timeService = new MutableTimeService(NOW_MILLIS);
        DefaultSnowflakeIdGenerator generator = new DefaultSnowflakeIdGenerator(SnowflakeConfig.defaults(), activeLease(1, 0, NOW_MILLIS + 10_000), timeService);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        int threadCount = 4;
        int idsPerThread = 500;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        ids.add(generator.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertEquals(threadCount * idsPerThread, ids.size());
        assertEquals(threadCount * idsPerThread, generator.generatedTotal());
    }

    private static MachineLease activeLease(int machineId, long lastTimestamp, long leaseExpiresAtMillis) {
        return new MachineLease(
                "xiaohashu",
                machineId,
                new InstanceIdentity("host:8080", false),
                MachineStatus.ACTIVE,
                lastTimestamp,
                Instant.EPOCH,
                Instant.ofEpochMilli(leaseExpiresAtMillis),
                0
        );
    }
}
