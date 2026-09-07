package com.dyc.xiaohashu.id.generator.core.snowflake;

import com.dyc.xiaohashu.id.generator.core.time.TimeService;

import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * Default bounded wait implementation for local clock rollback.
 */
public class DefaultClockBackwardsHandler implements ClockBackwardsHandler {

    private static final long NANOS_PER_MILLIS = 1_000_000L;

    private final ClockBackwardsPolicy policy;
    private final TimeService timeService;

    public DefaultClockBackwardsHandler(ClockBackwardsPolicy policy, TimeService timeService) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.timeService = Objects.requireNonNull(timeService, "timeService must not be null");
    }

    @Override
    public void handle(long lastTimestamp, long currentTimestamp) {
        long backwardsMillis = lastTimestamp - currentTimestamp;
        if (backwardsMillis <= 0) {
            return;
        }
        if (backwardsMillis > policy.maxWait().toMillis()) {
            throw new ClockBackwardsException("clock moved backwards by " + backwardsMillis + "ms");
        }
        if (backwardsMillis <= policy.spinThreshold().toMillis()) {
            while (timeService.currentTimeMillis() < lastTimestamp) {
                Thread.onSpinWait();
            }
            return;
        }
        LockSupport.parkNanos(backwardsMillis * NANOS_PER_MILLIS);
    }
}
