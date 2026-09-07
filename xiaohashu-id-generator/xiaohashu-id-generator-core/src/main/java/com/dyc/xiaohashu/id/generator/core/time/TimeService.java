package com.dyc.xiaohashu.id.generator.core.time;

import java.time.Instant;

/**
 * Abstracts wall-clock access so core algorithms can be unit tested deterministically.
 */
@FunctionalInterface
public interface TimeService {

    /**
     * Returns the current wall-clock instant.
     *
     * @return current instant
     */
    Instant now();

    /**
     * Returns the current wall-clock time in epoch milliseconds.
     *
     * @return current epoch milliseconds
     */
    default long currentTimeMillis() {
        return now().toEpochMilli();
    }
}
