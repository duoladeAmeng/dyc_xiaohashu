package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.snowflake.exception.ClockTooManyBackwardsException;

/**
 * Copied and modified from CosId's ClockBackwardsSynchronizer.
 */
public interface ClockBackwardsSynchronizer {

    void sync(long lastTimestamp) throws InterruptedException, ClockTooManyBackwardsException;

    default void syncUninterruptibly(long lastTimestamp) {
        try {
            sync(lastTimestamp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted during clock backwards sync.", e);
        }
    }

    static long getBackwardsTimestamp(long lastTimestamp) {
        return lastTimestamp - System.currentTimeMillis();
    }
}
