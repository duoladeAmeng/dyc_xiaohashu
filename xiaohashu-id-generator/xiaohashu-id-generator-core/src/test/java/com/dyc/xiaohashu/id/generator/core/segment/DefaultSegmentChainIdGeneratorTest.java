package com.dyc.xiaohashu.id.generator.core.segment;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSegmentChainIdGeneratorTest {

    @Test
    void constructorShouldPrefetchSafeDistance() {
        InMemorySegmentAllocator allocator = new InMemorySegmentAllocator();
        ManualSegmentPrefetchScheduler scheduler = new ManualSegmentPrefetchScheduler();
        DefaultSegmentChainIdGenerator generator = generator(allocator, scheduler, 2, 8);

        assertEquals(3, allocator.callCount());
        assertEquals(2, generator.tail().version());
        assertEquals(3, generator.segmentAllocatedTotal());
        assertEquals(2, generator.chainDistance());
        generator.close();
        assertTrue(generator.closed());
    }

    @Test
    void nextIdShouldUsePrefetchedChainBeforeFetchingAgain() {
        InMemorySegmentAllocator allocator = new InMemorySegmentAllocator();
        ManualSegmentPrefetchScheduler scheduler = new ManualSegmentPrefetchScheduler();
        DefaultSegmentChainIdGenerator generator = generator(allocator, scheduler, 2, 8);

        for (int i = 1; i <= 6; i++) {
            assertEquals(i, generator.nextId());
        }

        assertEquals(3, allocator.callCount());
        assertEquals(7, generator.nextId());
        assertEquals(4, allocator.callCount());
        assertEquals(7, generator.generatedTotal());
        assertEquals(4, generator.segmentAllocatedTotal());
        generator.close();
    }

    @Test
    void schedulerRunShouldRestoreSafeDistanceAfterHeadMoves() {
        InMemorySegmentAllocator allocator = new InMemorySegmentAllocator();
        ManualSegmentPrefetchScheduler scheduler = new ManualSegmentPrefetchScheduler();
        DefaultSegmentChainIdGenerator generator = generator(allocator, scheduler, 2, 8);
        generator.nextId();
        generator.nextId();
        generator.nextId();

        scheduler.runOnlyJob();

        assertEquals(4, allocator.callCount());
        assertEquals(3, generator.tail().version());
        assertEquals(1, generator.prefetchSuccessTotal());
        generator.close();
    }

    @Test
    void hungerShouldIncreasePrefetchDistance() {
        InMemorySegmentAllocator allocator = new InMemorySegmentAllocator();
        ManualSegmentPrefetchScheduler scheduler = new ManualSegmentPrefetchScheduler();
        DefaultSegmentChainIdGenerator generator = generator(allocator, scheduler, 1, 4);
        for (int i = 1; i <= 5; i++) {
            assertEquals(i, generator.nextId());
        }

        scheduler.runOnlyJob();

        assertEquals(2, generator.prefetchDistance());
        assertEquals(5, allocator.callCount());
        assertEquals(1, generator.prefetchSuccessTotal());
        generator.close();
    }

    @Test
    void existingPrefetchedSegmentsShouldContinueWhenAllocatorFails() {
        InMemorySegmentAllocator allocator = new InMemorySegmentAllocator();
        allocator.failAfter(3);
        ManualSegmentPrefetchScheduler scheduler = new ManualSegmentPrefetchScheduler();
        DefaultSegmentChainIdGenerator generator = generator(allocator, scheduler, 2, 8);

        for (int i = 1; i <= 6; i++) {
            assertEquals(i, generator.nextId());
        }

        assertThrows(RuntimeException.class, generator::nextId);
        assertEquals(1, generator.segmentFetchFailureTotal());
        generator.close();
    }

    @Test
    void prefetchShouldRecordFailureWhenAllocatorFails() {
        InMemorySegmentAllocator allocator = new InMemorySegmentAllocator();
        allocator.failAfter(3);
        ManualSegmentPrefetchScheduler scheduler = new ManualSegmentPrefetchScheduler();
        DefaultSegmentChainIdGenerator generator = generator(allocator, scheduler, 2, 8);

        assertEquals(1, generator.nextId());
        assertEquals(2, generator.nextId());

        assertThrows(RuntimeException.class, scheduler::runOnlyJob);
        assertEquals(1, generator.prefetchFailureTotal());
        assertEquals(1, generator.segmentFetchFailureTotal());
        generator.close();
    }

    @Test
    void nextIdShouldBeUniqueWhenCalledConcurrently() throws InterruptedException {
        InMemorySegmentAllocator allocator = new InMemorySegmentAllocator();
        ManualSegmentPrefetchScheduler scheduler = new ManualSegmentPrefetchScheduler();
        DefaultSegmentChainIdGenerator generator = new DefaultSegmentChainIdGenerator(
                "xiaohashu",
                "note",
                new SegmentIdConfig(10),
                chainConfig(2, 16),
                allocator,
                scheduler
        );
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        int threadCount = 8;
        int idsPerThread = 200;
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
        generator.close();
    }

    private static DefaultSegmentChainIdGenerator generator(
            InMemorySegmentAllocator allocator,
            ManualSegmentPrefetchScheduler scheduler,
            int safeDistance,
            int maxPrefetchDistance
    ) {
        return new DefaultSegmentChainIdGenerator(
                "xiaohashu",
                "note",
                new SegmentIdConfig(2),
                chainConfig(safeDistance, maxPrefetchDistance),
                allocator,
                scheduler
        );
    }

    private static SegmentChainConfig chainConfig(int safeDistance, int maxPrefetchDistance) {
        return new SegmentChainConfig(
                safeDistance,
                maxPrefetchDistance,
                Duration.ofSeconds(1),
                0,
                Duration.ofMillis(1)
        );
    }

    private static final class InMemorySegmentAllocator implements SegmentAllocator {

        private final AtomicLong maxId = new AtomicLong();
        private final AtomicInteger callCount = new AtomicInteger();
        private volatile int failAfter = Integer.MAX_VALUE;

        @Override
        public synchronized IdSegment nextSegment(String namespace, String tag, long requestedStep) {
            int call = callCount.incrementAndGet();
            if (call > failAfter) {
                throw new RuntimeException("allocator unavailable");
            }
            long end = maxId.addAndGet(requestedStep);
            return new IdSegment(namespace, tag, end - requestedStep + 1, end);
        }

        private int callCount() {
            return callCount.get();
        }

        private void failAfter(int value) {
            this.failAfter = value;
        }
    }

    private static final class ManualSegmentPrefetchScheduler implements SegmentPrefetchScheduler {

        private final Map<String, Runnable> jobs = new ConcurrentHashMap<>();

        @Override
        public void register(String jobId, Runnable job) {
            jobs.put(jobId, job);
        }

        @Override
        public void wakeup(String jobId) {
        }

        @Override
        public void unregister(String jobId) {
            jobs.remove(jobId);
        }

        @Override
        public void close() {
            jobs.clear();
        }

        private void runOnlyJob() {
            assertEquals(1, jobs.size());
            jobs.values().iterator().next().run();
        }
    }
}
