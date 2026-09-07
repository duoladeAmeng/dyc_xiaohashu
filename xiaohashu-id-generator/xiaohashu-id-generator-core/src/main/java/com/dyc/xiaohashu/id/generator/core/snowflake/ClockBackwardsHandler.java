package com.dyc.xiaohashu.id.generator.core.snowflake;

/**
 * Handles local wall-clock rollback before a Snowflake generator retries.
 */
public interface ClockBackwardsHandler {

    /**
     * Waits or rejects according to local rollback policy.
     *
     * @param lastTimestamp last timestamp used by the generator, in milliseconds
     * @param currentTimestamp current wall-clock timestamp, in milliseconds
     */
    void handle(long lastTimestamp, long currentTimestamp);
}
