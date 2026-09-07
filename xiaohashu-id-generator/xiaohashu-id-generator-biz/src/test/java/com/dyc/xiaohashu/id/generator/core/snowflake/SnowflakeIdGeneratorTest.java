package com.dyc.xiaohashu.id.generator.core.snowflake;

import org.junit.jupiter.api.Test;

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

class SnowflakeIdGeneratorTest {

    @Test
    void shouldGenerateUniqueIdsConcurrently() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(
                Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(), 41, 10, 12, 1
        );
        int threads = 8;
        int perThread = 1000;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.execute(() -> {
                for (int j = 0; j < perThread; j++) {
                    ids.add(generator.nextId());
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertEquals(threads * perThread, ids.size());
    }

    @Test
    void shouldRejectInvalidMachineId() {
        assertThrows(IllegalArgumentException.class, () ->
                new SnowflakeIdGenerator(Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(), 41, 1, 12, 2)
        );
    }
}
