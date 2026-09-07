package com.dyc.xiaohashu.id.generator.core.segment;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSegmentPrefetchSchedulerTest {

    @Test
    void wakeupShouldRunRegisteredJob() throws InterruptedException {
        try (DefaultSegmentPrefetchScheduler scheduler = new DefaultSegmentPrefetchScheduler(Duration.ofSeconds(10), 1)) {
            CountDownLatch latch = new CountDownLatch(1);
            scheduler.register("job", latch::countDown);

            scheduler.wakeup("job");

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void wakeupShouldCoalesceConcurrentRunsForSameJob() throws InterruptedException {
        try (DefaultSegmentPrefetchScheduler scheduler = new DefaultSegmentPrefetchScheduler(Duration.ofSeconds(10), 2)) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger runCount = new AtomicInteger();
            scheduler.register("job", () -> {
                runCount.incrementAndGet();
                entered.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });

            scheduler.wakeup("job");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            scheduler.wakeup("job");
            scheduler.wakeup("job");
            release.countDown();

            Thread.sleep(100);
            assertEquals(1, runCount.get());
        }
    }

    @Test
    void wakeupShouldRecordFailedRunsWithoutKeepingJobInFlight() throws InterruptedException {
        try (DefaultSegmentPrefetchScheduler scheduler = new DefaultSegmentPrefetchScheduler(Duration.ofSeconds(10), 1)) {
            CountDownLatch firstRun = new CountDownLatch(1);
            CountDownLatch secondRun = new CountDownLatch(1);
            AtomicInteger runCount = new AtomicInteger();
            scheduler.register("job", () -> {
                int count = runCount.incrementAndGet();
                if (count == 1) {
                    firstRun.countDown();
                    throw new RuntimeException("boom");
                }
                secondRun.countDown();
            });

            scheduler.wakeup("job");
            assertTrue(firstRun.await(2, TimeUnit.SECONDS));
            waitUntilFailedRunIsRecorded(scheduler);
            scheduler.wakeup("job");

            assertTrue(secondRun.await(2, TimeUnit.SECONDS));
            assertEquals(1, scheduler.failedRunTotal());
        }
    }

    private static void waitUntilFailedRunIsRecorded(DefaultSegmentPrefetchScheduler scheduler) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (scheduler.failedRunTotal() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, scheduler.failedRunTotal());
    }
}
