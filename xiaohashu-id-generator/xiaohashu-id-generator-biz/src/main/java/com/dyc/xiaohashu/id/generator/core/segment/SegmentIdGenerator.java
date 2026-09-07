package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.IdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.grouped.GroupedAccessor;

public class SegmentIdGenerator implements IdGenerator {

    public static final int ONE_STEP = 1;

    private final long idSegmentTtl;
    private final SegmentAllocator segmentAllocator;
    private volatile IdSegment segment = DefaultIdSegment.OVERFLOW;

    public SegmentIdGenerator(SegmentAllocator segmentAllocator) {
        this(IdSegment.TIME_TO_LIVE_FOREVER, segmentAllocator);
    }

    public SegmentIdGenerator(long idSegmentTtl, SegmentAllocator segmentAllocator) {
        if (idSegmentTtl <= 0) {
            throw new IllegalArgumentException(String.format("idSegmentTtl:[%s] must be greater than 0.", idSegmentTtl));
        }
        this.idSegmentTtl = idSegmentTtl;
        this.segmentAllocator = segmentAllocator;
    }

    public IdSegment current() {
        return segment;
    }

    @Override
    public long generate() {
        if (segmentAllocator.getStep() == ONE_STEP) {
            GroupedAccessor.setIfNotNever(segmentAllocator.group());
            return segmentAllocator.nextMaxId();
        }
        long nextSeq;
        if (segment.isAvailable()) {
            nextSeq = segment.incrementAndGet();
            if (!segment.isOverflow(nextSeq)) {
                return nextSeq;
            }
        }

        synchronized (this) {
            while (true) {
                if (segment.isAvailable()) {
                    nextSeq = segment.incrementAndGet();
                    if (!segment.isOverflow(nextSeq)) {
                        return nextSeq;
                    }
                }
                IdSegment nextSegment = segmentAllocator.nextIdSegment(idSegmentTtl);
                if (!segmentAllocator.allowReset()) {
                    segment.ensureNextIdSegment(nextSegment);
                }
                segment = nextSegment;
            }
        }
    }
}
