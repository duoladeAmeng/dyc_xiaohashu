package com.dyc.xiaohashu.id.generator.core.segment;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Single node in a segment chain.
 */
public final class SegmentChainNode {

    private final long version;
    private final IdSegment segment;
    private final AtomicReference<SegmentChainNode> next = new AtomicReference<>();

    public SegmentChainNode(long version, IdSegment segment) {
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
        this.segment = Objects.requireNonNull(segment, "segment must not be null");
    }

    /**
     * Attempts to set next once.
     *
     * @param nextSupplier supplier for the next node
     * @return true if this call installed next
     */
    public boolean trySetNext(Supplier<SegmentChainNode> nextSupplier) {
        Objects.requireNonNull(nextSupplier, "nextSupplier must not be null");
        synchronized (this) {
            if (next.get() != null) {
                return false;
            }
            SegmentChainNode candidate = Objects.requireNonNull(nextSupplier.get(), "nextSupplier returned null");
            if (candidate.version <= version) {
                throw new IllegalArgumentException("next version must be greater than current version");
            }
            next.set(candidate);
            return true;
        }
    }

    /**
     * Returns the next node.
     *
     * @return next node or null
     */
    public SegmentChainNode next() {
        return next.get();
    }

    /**
     * Returns node version.
     *
     * @return version
     */
    public long version() {
        return version;
    }

    /**
     * Returns the local segment.
     *
     * @return segment
     */
    public IdSegment segment() {
        return segment;
    }
}
