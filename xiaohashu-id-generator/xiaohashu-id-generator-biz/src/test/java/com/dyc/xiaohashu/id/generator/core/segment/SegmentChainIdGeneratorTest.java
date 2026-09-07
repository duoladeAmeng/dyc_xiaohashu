package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.segment.concurrent.PrefetchWorkerExecutorService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentChainIdGeneratorTest {

    @Test
    void shouldGenerateUniqueIdsConcurrentlyWithPrefetchChain() throws InterruptedException {
        PrefetchWorkerExecutorService prefetchWorkers = new PrefetchWorkerExecutorService(Duration.ofMillis(10), 2);
        try {
            SegmentChainIdGenerator generator = new SegmentChainIdGenerator(
                    IdSegment.TIME_TO_LIVE_FOREVER,
                    2,
                    new SegmentIdGeneratorTest.AtomicSegmentAllocator("segment-chain", 64),
                    prefetchWorkers
            );
            int threads = 8;
            int perThread = 1000;
            Set<Long> ids = ConcurrentHashMap.newKeySet();
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    for (int j = 0; j < perThread; j++) {
                        ids.add(generator.generate());
                    }
                    latch.countDown();
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdownNow();
            assertEquals(threads * perThread, ids.size());
        } finally {
            prefetchWorkers.shutdown();
        }
    }
}
