package com.dyc.xiaohashu.id.generator.core.snowflake.exception;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

public class TimestampOverflowException extends IdGeneratorException {

    public TimestampOverflowException(long epoch, long diffTimestamp, long maxTimestamp) {
        super("Timestamp overflow. epoch: " + epoch + ", diffTimestamp: " + diffTimestamp + ", maxTimestamp: " + maxTimestamp);
    }
}
