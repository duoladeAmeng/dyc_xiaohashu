package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.snowflake.exception.ClockTooManyBackwardsException;
import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Migrated from CosId's DefaultClockBackwardsSynchronizer.
 */
public class DefaultClockBackwardsSynchronizer implements ClockBackwardsSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(DefaultClockBackwardsSynchronizer.class);
    public static final int DEFAULT_SPIN_THRESHOLD = 1;
    public static final int DEFAULT_BROKEN_THRESHOLD = 500;

    private final int spinThreshold;
    private final int brokenThreshold;

    public DefaultClockBackwardsSynchronizer() {
        this(DEFAULT_SPIN_THRESHOLD, DEFAULT_BROKEN_THRESHOLD);
    }

    public DefaultClockBackwardsSynchronizer(int spinThreshold, int brokenThreshold) {
        if (spinThreshold <= 0) {
            throw new IllegalArgumentException("spinThreshold must be greater than 0.");
        }
        if (brokenThreshold <= spinThreshold) {
            throw new IllegalArgumentException("brokenThreshold must be greater than spinThreshold.");
        }
        this.spinThreshold = spinThreshold;
        this.brokenThreshold = brokenThreshold;
    }

    @Override
    public void sync(long lastTimestamp) throws InterruptedException, ClockTooManyBackwardsException {
        long backwards = ClockBackwardsSynchronizer.getBackwardsTimeStamp(lastTimestamp);
        if (backwards <= 0) {
            return;
        }
        log.warn("Detected clock backwards: {} ms, lastTimestamp: {}.", backwards, lastTimestamp);
        if (backwards <= spinThreshold) {
            while (ClockBackwardsSynchronizer.getBackwardsTimeStamp(lastTimestamp) > 0) {
                Thread.onSpinWait();
            }
            return;
        }
        if (backwards > brokenThreshold) {
            throw new ClockTooManyBackwardsException(lastTimestamp, System.currentTimeMillis(), brokenThreshold);
        }
        TimeUnit.MILLISECONDS.sleep(backwards);
    }

    @Override
    public void syncUninterruptibly(long lastTimestamp) throws ClockTooManyBackwardsException {
        try {
            sync(lastTimestamp);
        } catch (InterruptedException e) {
            log.error("Thread interrupted during sync - lastTimestamp:[{}]. Restoring interrupt status.", lastTimestamp, e);
            Thread.currentThread().interrupt();
            throw new IdGeneratorException(e);
        }
    }
}
