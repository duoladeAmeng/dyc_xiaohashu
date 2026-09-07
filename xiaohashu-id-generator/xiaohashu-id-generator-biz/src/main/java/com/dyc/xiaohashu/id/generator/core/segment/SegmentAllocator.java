package com.dyc.xiaohashu.id.generator.core.segment;

/**
 * 号段分配抽象。当前只提供 JDBC 实现，生成器核心不感知数据库。
 */
public interface SegmentAllocator {

    int DEFAULT_SEGMENTS = 1;
    long DEFAULT_STEP = 10;
    long DEFAULT_OFFSET = 0;

    String getNamespace();

    String getName();

    long getStep();

    long nextMaxId(long step);

    default String getNamespacedName() {
        return getNamespace() + "." + getName();
    }

    default long getStep(int segments) {
        return Math.multiplyExact(getStep(), segments);
    }

    default boolean allowReset() {
        return true;
    }

    default long nextMaxId() {
        return nextMaxId(getStep());
    }

    default IdSegment nextIdSegment() {
        return nextIdSegment(IdSegment.TIME_TO_LIVE_FOREVER);
    }

    default IdSegment nextIdSegment(long ttl) {
        if (ttl <= 0) {
            throw new IllegalArgumentException("ttl must be greater than 0.");
        }
        return new DefaultIdSegment(nextMaxId(), getStep(), Clock.secondTime(), ttl);
    }

    default IdSegment nextIdSegment(int segments, long ttl) {
        if (segments <= 0) {
            throw new IllegalArgumentException("segments must be greater than 0.");
        }
        long totalStep = getStep(segments);
        return new MergedIdSegment(segments, new DefaultIdSegment(nextMaxId(totalStep), totalStep, Clock.secondTime(), ttl));
    }

    default IdSegmentChain nextIdSegmentChain(IdSegmentChain previousChain, int segments, long ttl) {
        IdSegment nextIdSegment = segments == DEFAULT_SEGMENTS ? nextIdSegment(ttl) : nextIdSegment(segments, ttl);
        return new IdSegmentChain(previousChain, nextIdSegment, allowReset());
    }

    static void ensureStep(long step) {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be greater than 0.");
        }
    }
}
