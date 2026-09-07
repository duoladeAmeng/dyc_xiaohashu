package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;

/**
 * Raised when the current instance can no longer refresh or release its lease.
 */
public class MachineLeaseLostException extends GeneratorUnavailableException {

    public MachineLeaseLostException(String message) {
        super(message);
    }
}
