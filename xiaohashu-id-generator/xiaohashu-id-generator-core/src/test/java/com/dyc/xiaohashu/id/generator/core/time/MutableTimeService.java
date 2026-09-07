package com.dyc.xiaohashu.id.generator.core.time;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable clock for deterministic generator tests.
 */
public class MutableTimeService implements TimeService {

    private final AtomicLong currentMillis;
    private final Queue<Long> scriptedMillis = new ArrayDeque<>();

    public MutableTimeService(long currentMillis) {
        this.currentMillis = new AtomicLong(currentMillis);
    }

    public void setMillis(long millis) {
        currentMillis.set(millis);
    }

    public void script(long... millis) {
        scriptedMillis.clear();
        for (long value : millis) {
            scriptedMillis.add(value);
        }
    }

    @Override
    public Instant now() {
        return Instant.ofEpochMilli(currentTimeMillis());
    }

    @Override
    public long currentTimeMillis() {
        Long scripted = scriptedMillis.poll();
        if (scripted != null) {
            currentMillis.set(scripted);
            return scripted;
        }
        return currentMillis.get();
    }
}
