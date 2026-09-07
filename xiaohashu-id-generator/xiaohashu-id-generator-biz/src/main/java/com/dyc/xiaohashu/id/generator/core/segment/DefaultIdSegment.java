package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.segment.grouped.GroupedAccessor;
import com.dyc.xiaohashu.id.generator.core.segment.grouped.GroupedKey;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class DefaultIdSegment implements IdSegment {

    public static final DefaultIdSegment OVERFLOW = new DefaultIdSegment(SEQUENCE_OVERFLOW, 0, Clock.SYSTEM.secondTime(), TIME_TO_LIVE_FOREVER, GroupedKey.NEVER);

    private static final AtomicLongFieldUpdater<DefaultIdSegment> SEQUENCE =
            AtomicLongFieldUpdater.newUpdater(DefaultIdSegment.class, "sequence");

    private final long maxId;
    private final long offset;
    private final long step;
    private volatile long sequence;
    private final long fetchTime;
    private final long ttl;
    private final GroupedKey group;

    public DefaultIdSegment(long maxId, long step) {
        this(maxId, step, Clock.SYSTEM.secondTime(), TIME_TO_LIVE_FOREVER, GroupedKey.NEVER);
    }

    public DefaultIdSegment(long maxId, long step, long fetchTime, long ttl) {
        this(maxId, step, fetchTime, ttl, GroupedKey.NEVER);
    }

    public DefaultIdSegment(long maxId, long step, long fetchTime, long ttl, GroupedKey group) {
        if (ttl <= 0) {
            throw new IllegalArgumentException(String.format("ttl:[%s] must be greater than 0.", ttl));
        }
        this.maxId = maxId;
        this.step = step;
        this.offset = maxId - step;
        this.sequence = offset;
        this.fetchTime = fetchTime;
        this.ttl = ttl;
        this.group = group;
    }

    @Override
    public GroupedKey group() {
        return group;
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
        GroupedAccessor.setIfNotNever(group());
        return nextSeq;
    }

    @Override
    public String toString() {
        return "DefaultIdSegment{maxId=" + maxId + ", offset=" + offset + ", step=" + step + ", sequence=" + sequence + ", fetchTime=" + fetchTime + ", ttl=" + ttl + '}';
    }
}
