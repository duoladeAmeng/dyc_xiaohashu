package com.dyc.xiaohashu.id.generator.core.snowflake;

import com.dyc.xiaohashu.id.generator.core.IdGenerator;
import com.dyc.xiaohashu.id.generator.core.machine.ClockBackwardsSynchronizer;
import com.dyc.xiaohashu.id.generator.core.snowflake.exception.ClockBackwardsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClockSyncSnowflakeIdGenerator implements IdGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClockSyncSnowflakeIdGenerator.class);

    private final SnowflakeIdGenerator actual;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;

    public ClockSyncSnowflakeIdGenerator(SnowflakeIdGenerator actual) {
        this(actual, ClockBackwardsSynchronizer.DEFAULT);
    }

    public ClockSyncSnowflakeIdGenerator(SnowflakeIdGenerator actual, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this.actual = actual;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
    }

    @Override
    public long generate() {
        try {
            return actual.generate();
        } catch (ClockBackwardsException exception) {
            log.warn(exception.getMessage(), exception);
            clockBackwardsSynchronizer.syncUninterruptibly(actual.getLastTimestamp());
            return actual.generate();
        }
    }

    public SnowflakeIdGenerator getActual() {
        return actual;
    }
}
