package com.dyc.xiaohashu.id.generator.core;

/**
 * Generates globally unique numeric IDs.
 */
public interface IdGenerator {

    /**
     * Returns the next unique ID.
     *
     * @return next unique ID
     */
    long nextId();

    /**
     * Returns the next unique ID as a decimal string.
     *
     * @return next unique ID string
     */
    default String nextIdAsString() {
        return Long.toString(nextId());
    }
}
