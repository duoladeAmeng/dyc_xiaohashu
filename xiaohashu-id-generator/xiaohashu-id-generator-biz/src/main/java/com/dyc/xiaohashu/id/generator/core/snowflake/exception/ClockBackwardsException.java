package com.dyc.xiaohashu.id.generator.core.snowflake.exception;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

public class ClockBackwardsException extends IdGeneratorException {

    public ClockBackwardsException(long lastTimestamp, long currentTimestamp) {
        super("Clock moved backwards. Refusing to generate id. lastTimestamp: " + lastTimestamp + ", currentTimestamp: " + currentTimestamp);
    }
}
