package com.dyc.xiaohashu.id.generator.core.segment;

/**
 * Contract for segment-chain ID generators.
 */
public interface SegmentChainIdGenerator extends SegmentIdGenerator {

    /**
     * Returns the currently visible chain head.
     *
     * @return chain head
     */
    SegmentChainNode head();

    /**
     * Returns the currently visible chain tail.
     *
     * @return chain tail
     */
    SegmentChainNode tail();

    /**
     * Returns the adaptive prefetch distance in segments.
     *
     * @return prefetch distance
     */
    int prefetchDistance();

    /**
     * Returns the current number of segments between head and tail.
     *
     * @return current chain distance
     */
    int chainDistance();

    /**
     * Returns whether this generator has been closed.
     *
     * @return true when closed
     */
    boolean closed();

    /**
     * Returns the number of successful background prefetch runs.
     *
     * @return successful prefetch runs
     */
    long prefetchSuccessTotal();

    /**
     * Returns the number of failed background prefetch runs.
     *
     * @return failed prefetch runs
     */
    long prefetchFailureTotal();
}
