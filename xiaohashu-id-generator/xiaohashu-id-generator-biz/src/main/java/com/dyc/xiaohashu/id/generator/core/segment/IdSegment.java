package com.dyc.xiaohashu.id.generator.core.segment;

/**
 * Copied and modified from CosId's IdSegment.
 */
public interface IdSegment extends Comparable<IdSegment> {

    long SEQUENCE_OVERFLOW = -1;
    long TIME_TO_LIVE_FOREVER = Long.MAX_VALUE;

    long getFetchTime();

    long getMaxId();

    long getOffset();

    long getSequence();

    long getStep();

    default long getTtl() {
        return TIME_TO_LIVE_FOREVER;
    }

    default boolean isExpired() {
        if (TIME_TO_LIVE_FOREVER == getTtl()) {
            return false;
        }
        return Clock.secondTime() - getFetchTime() > getTtl();
    }

    default boolean isOverflow() {
        return getSequence() >= getMaxId();
    }

    default boolean isOverflow(long nextSeq) {
        return nextSeq == SEQUENCE_OVERFLOW || nextSeq > getMaxId();
    }

    default boolean isAvailable() {
        return !isExpired() && !isOverflow();
    }

    long incrementAndGet();

    @Override
    default int compareTo(IdSegment other) {
        if (getOffset() == other.getOffset()) {
            return 0;
        }
        return getOffset() > other.getOffset() ? 1 : -1;
    }

    default void ensureNextIdSegment(IdSegment nextIdSegment) {
        if (compareTo(nextIdSegment) >= 0) {
            throw new NextIdSegmentExpiredException(this, nextIdSegment);
        }
    }
}
