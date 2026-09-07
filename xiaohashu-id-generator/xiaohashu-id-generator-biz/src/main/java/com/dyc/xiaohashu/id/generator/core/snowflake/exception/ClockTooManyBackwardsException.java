package com.dyc.xiaohashu.id.generator.core.snowflake.exception;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

public class ClockTooManyBackwardsException extends IdGeneratorException {

    public ClockTooManyBackwardsException(long lastTimestamp, long currentTimestamp, long brokenThreshold) {
        super("Clock moved backwards too many. brokenThreshold: " + brokenThreshold + ", lastTimestamp: " + lastTimestamp + ", currentTimestamp: " + currentTimestamp);
    }
}
