package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.snowflake.exception.ClockTooManyBackwardsException;

/**
 * Migrated from CosId's ClockBackwardsSynchronizer.
 */
public interface ClockBackwardsSynchronizer {

    ClockBackwardsSynchronizer DEFAULT = new DefaultClockBackwardsSynchronizer();

    void sync(long lastTimestamp) throws InterruptedException, ClockTooManyBackwardsException;

    void syncUninterruptibly(long lastTimestamp);

    static long getBackwardsTimeStamp(long lastTimestamp) {
        return lastTimestamp - System.currentTimeMillis();
    }
}
