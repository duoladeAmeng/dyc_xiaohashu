package com.dyc.xiaohashu.id.generator.core.time;

import java.time.Instant;

/**
 * Production {@link TimeService} backed by {@link Instant#now()}.
 */
public final class SystemTimeService implements TimeService {

    public static final SystemTimeService INSTANCE = new SystemTimeService();

    private SystemTimeService() {
    }

    @Override
    public Instant now() {
        return Instant.now();
    }
}
