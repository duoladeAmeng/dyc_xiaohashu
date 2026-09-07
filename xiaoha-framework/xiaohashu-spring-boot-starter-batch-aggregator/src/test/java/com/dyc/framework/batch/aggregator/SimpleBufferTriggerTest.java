package com.dyc.framework.batch.aggregator;

import org.junit.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimpleBufferTriggerTest {

    @Test
    public void shouldAggregateElementsByInterval() throws Exception {
        AtomicReference<Map<String, Integer>> consumed = new AtomicReference<>();
        CountDownLatch consumedLatch = new CountDownLatch(1);
        BufferTrigger<String> trigger = BufferTrigger.<String, Map<String, Integer>>simple()
                .name("note-like")
                .setContainerEx(ConcurrentHashMap::new, (map, noteId) -> {
                    map.merge(noteId, 1, Integer::sum);
                    return 1;
                })
                .interval(50, TimeUnit.MILLISECONDS)
                .consumer(map -> {
                    consumed.set(map);
                    consumedLatch.countDown();
                })
                .build();

        trigger.enqueue("note-1");
        trigger.enqueue("note-1");
        trigger.enqueue("note-2");

        try {
            assertTrue(consumedLatch.await(2, TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(2), consumed.get().get("note-1"));
            assertEquals(Integer.valueOf(1), consumed.get().get("note-2"));
        } finally {
            trigger.close();
        }
    }

    @Test
    public void shouldFlushPendingElementsWhenClose() {
        AtomicReference<Map<String, Integer>> consumed = new AtomicReference<>();
        BufferTrigger<String> trigger = SimpleBufferTrigger.<String>newCounterBuilder()
                .interval(1, TimeUnit.DAYS)
                .consumer(consumed::set)
                .build();

        trigger.enqueue("note-1");
        assertTrue(trigger.getPendingChanges() > 0);

        trigger.close();

        assertEquals(Integer.valueOf(1), consumed.get().get("note-1"));
        assertEquals(0, trigger.getPendingChanges());
    }
}
