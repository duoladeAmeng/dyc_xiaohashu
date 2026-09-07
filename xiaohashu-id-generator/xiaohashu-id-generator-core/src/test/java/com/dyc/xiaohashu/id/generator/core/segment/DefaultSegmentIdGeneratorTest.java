package com.dyc.xiaohashu.id.generator.core.segment;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSegmentIdGeneratorTest {

    @Test
    void nextIdShouldUseCurrentSegmentBeforeFetchingAnotherOne() {
        InMemorySegmentAllocator allocator = new InMemorySegmentAllocator();
        DefaultSegmentIdGenerator generator = new DefaultSegmentIdGenerator("xiaohashu", "note", new SegmentIdConfig(3), allocator);

        assertEquals(1, allocator.callCount());
        assertEquals(1, generator.nextId());
        assertEquals(2, generator.nextId());
        assertEquals(3, generator.nextId());
        assertEquals(1, allocator.callCount());
        assertEquals(4, generator.nextId());
        assertEquals(2, allocator.callCount());
        assertEquals(4, generator.generatedTotal());
        assertEquals(2, generator.segmentAllocatedTotal());
        assertEquals(0, generator.segmentFetchFailureTotal());
    }

    @Test
    void nextIdShouldBeUniqueWhenCalledConcurrently() throws InterruptedException {
        InMemorySegmentAllocator allocator = new InMemorySegmentAllocator();
        DefaultSegmentIdGenerator generator = new DefaultSegmentIdGenerator("xiaohashu", "note", new SegmentIdConfig(10), allocator);
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
        assertEquals(threadCount * idsPerThread, generator.generatedTotal());
    }

    private static final class InMemorySegmentAllocator implements SegmentAllocator {

        private final AtomicLong maxId = new AtomicLong();
        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public synchronized IdSegment nextSegment(String namespace, String tag, long requestedStep) {
            callCount.incrementAndGet();
            long end = maxId.addAndGet(requestedStep);
            return new IdSegment(namespace, tag, end - requestedStep + 1, end);
        }

        private int callCount() {
            return callCount.get();
        }
    }
}
