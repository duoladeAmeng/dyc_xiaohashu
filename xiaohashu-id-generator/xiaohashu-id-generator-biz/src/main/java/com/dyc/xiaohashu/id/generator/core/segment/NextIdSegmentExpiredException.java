package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Migrated from CosId's NextIdSegmentExpiredException.
 */
public class NextIdSegmentExpiredException extends IdGeneratorException {

    private static final AtomicLong TIMES = new AtomicLong();

    public NextIdSegmentExpiredException(IdSegment current, IdSegment next) {
        super("The next IdSegment " + next + " cannot be before the current IdSegment " + current + ", times: " + TIMES.incrementAndGet());
    }
}
