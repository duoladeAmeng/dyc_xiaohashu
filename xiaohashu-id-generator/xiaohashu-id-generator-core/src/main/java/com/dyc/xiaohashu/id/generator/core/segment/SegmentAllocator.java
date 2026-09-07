package com.dyc.xiaohashu.id.generator.core.segment;

/**
 * Allocates globally unique ID segments for segment-based generators.
 */
public interface SegmentAllocator {

    /**
     * Allocates the next non-overlapping ID segment.
     *
     * @param namespace generator namespace
     * @param tag business tag
     * @param requestedStep requested number of IDs
     * @return allocated segment
     */
    IdSegment nextSegment(String namespace, String tag, long requestedStep);
}
