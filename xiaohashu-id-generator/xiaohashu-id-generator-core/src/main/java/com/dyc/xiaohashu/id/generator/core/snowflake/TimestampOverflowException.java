package com.dyc.xiaohashu.id.generator.core.snowflake;

import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;

/**
 * Raised when the configured timestamp bit range has been exhausted.
 */
public class TimestampOverflowException extends GeneratorUnavailableException {

    public TimestampOverflowException(String message) {
        super(message);
    }
}
