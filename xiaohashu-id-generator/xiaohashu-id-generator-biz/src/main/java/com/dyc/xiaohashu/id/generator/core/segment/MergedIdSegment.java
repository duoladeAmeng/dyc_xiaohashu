package com.dyc.xiaohashu.id.generator.core.segment;

/**
 * Copied and modified from CosId's MergedIdSegment.
 */
public class MergedIdSegment implements IdSegment {

    private final int segments;
    private final IdSegment idSegment;
    private final long singleStep;

    public MergedIdSegment(int segments, IdSegment idSegment) {
        this.segments = segments;
        this.idSegment = idSegment;
        this.singleStep = idSegment.getStep() / segments;
    }

    public int getSegments() {
        return segments;
    }

    public long getSingleStep() {
        return singleStep;
    }

    @Override
    public long getFetchTime() {
        return idSegment.getFetchTime();
    }

    @Override
    public long getMaxId() {
        return idSegment.getMaxId();
    }

    @Override
    public long getOffset() {
        return idSegment.getOffset();
    }

    @Override
    public long getSequence() {
        return idSegment.getSequence();
    }

    @Override
    public long getStep() {
        return idSegment.getStep();
    }

    @Override
    public long getTtl() {
        return idSegment.getTtl();
    }

    @Override
    public long incrementAndGet() {
        return idSegment.incrementAndGet();
    }
}
