package com.dyc.xiaohashu.id.generator.core.segment;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Copied and modified from CosId's DefaultIdSegment.
 */
public class DefaultIdSegment implements IdSegment {

    public static final DefaultIdSegment OVERFLOW = new DefaultIdSegment(SEQUENCE_OVERFLOW, 0, Clock.secondTime(), TIME_TO_LIVE_FOREVER);

    private static final AtomicLongFieldUpdater<DefaultIdSegment> SEQUENCE =
            AtomicLongFieldUpdater.newUpdater(DefaultIdSegment.class, "sequence");

    private final long maxId;
    private final long offset;
    private final long step;
    private volatile long sequence;
    private final long fetchTime;
    private final long ttl;

    public DefaultIdSegment(long maxId, long step) {
        this(maxId, step, Clock.secondTime(), TIME_TO_LIVE_FOREVER);
    }

    public DefaultIdSegment(long maxId, long step, long fetchTime, long ttl) {
        if (ttl <= 0) {
            throw new IllegalArgumentException("ttl must be greater than 0.");
        }
        this.maxId = maxId;
        this.step = step;
        this.offset = maxId - step;
        this.sequence = offset;
        this.fetchTime = fetchTime;
        this.ttl = ttl;
    }

    @Override
    public long getFetchTime() {
        return fetchTime;
    }

    @Override
    public long getMaxId() {
        return maxId;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public long getSequence() {
        return sequence;
    }

    @Override
    public long getStep() {
        return step;
    }

    @Override
    public long getTtl() {
        return ttl;
    }

    @Override
    public long incrementAndGet() {
        if (isOverflow()) {
            return SEQUENCE_OVERFLOW;
        }
        long nextSeq = SEQUENCE.incrementAndGet(this);
        if (isOverflow(nextSeq)) {
            return SEQUENCE_OVERFLOW;
        }
        return nextSeq;
    }

    @Override
    public String toString() {
        return "DefaultIdSegment{maxId=" + maxId + ", offset=" + offset + ", step=" + step + ", sequence=" + sequence + ", fetchTime=" + fetchTime + ", ttl=" + ttl + '}';
    }
}
