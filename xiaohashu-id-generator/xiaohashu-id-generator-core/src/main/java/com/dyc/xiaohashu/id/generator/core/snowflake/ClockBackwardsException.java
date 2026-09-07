package com.dyc.xiaohashu.id.generator.core.snowflake;

import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;

/**
 * Raised when local wall-clock rollback exceeds the configured wait policy.
 */
public class ClockBackwardsException extends GeneratorUnavailableException {

    public ClockBackwardsException(String message) {
        super(message);
    }
}
