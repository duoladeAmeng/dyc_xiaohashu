package com.dyc.xiaohashu.id.generator.core.segment;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentIdGeneratorTest {

    @Test
    void shouldSwitchSegmentWhenCurrentSegmentOverflows() {
        SegmentIdGenerator generator = new SegmentIdGenerator(new AtomicSegmentAllocator("segment", 3));

        assertEquals(1, generator.nextId());
        assertEquals(2, generator.nextId());
        assertEquals(3, generator.nextId());
        assertEquals(4, generator.nextId());
    }

    @Test
    void shouldGenerateUniqueIdsConcurrently() throws InterruptedException {
        SegmentIdGenerator generator = new SegmentIdGenerator(new AtomicSegmentAllocator("segment", 32));
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

    static class AtomicSegmentAllocator implements SegmentAllocator {
        private final String name;
        private final long step;
        private final AtomicLong maxId = new AtomicLong();

        AtomicSegmentAllocator(String name, long step) {
            this.name = name;
            this.step = step;
        }

        @Override
        public String getNamespace() {
            return "test";
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getStep() {
            return step;
        }

        @Override
        public long nextMaxId(long step) {
            return maxId.addAndGet(step);
        }
    }
}
