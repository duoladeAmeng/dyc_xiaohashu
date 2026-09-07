package com.dyc.xiaohashu.id.generator.core.segment;

import java.util.function.Function;

/**
 * Copied and modified from CosId's IdSegmentChain.
 */
public class IdSegmentChain implements IdSegment {

    public static final int ROOT_VERSION = -1;
    public static final IdSegmentChain NOT_SET = null;

    private final long version;
    private final IdSegment idSegment;
    private volatile IdSegmentChain next;
    private final boolean allowReset;

    public IdSegmentChain(IdSegmentChain previousChain, IdSegment idSegment, boolean allowReset) {
        this(previousChain.getVersion() + 1, idSegment, allowReset);
    }

    public IdSegmentChain(long version, IdSegment idSegment, boolean allowReset) {
        this.version = version;
        this.idSegment = idSegment;
        this.allowReset = allowReset;
    }

    public boolean trySetNext(Function<IdSegmentChain, IdSegmentChain> idSegmentChainSupplier) {
        if (NOT_SET != next) {
            return false;
        }
        synchronized (this) {
            if (NOT_SET != next) {
                return false;
            }
            setNext(idSegmentChainSupplier.apply(this));
            return true;
        }
    }

    public void setNext(IdSegmentChain nextIdSegmentChain) {
        if (!allowReset) {
            ensureNextIdSegment(nextIdSegmentChain);
        }
        next = nextIdSegmentChain;
    }

    public IdSegmentChain ensureSetNext(Function<IdSegmentChain, IdSegmentChain> idSegmentChainSupplier) {
        IdSegmentChain currentChain = this;
        while (!currentChain.trySetNext(idSegmentChainSupplier)) {
            currentChain = currentChain.getNext();
        }
        return currentChain;
    }

    public IdSegmentChain getNext() {
        return next;
    }

    public IdSegment getIdSegment() {
        return idSegment;
    }

    public long getVersion() {
        return version;
    }

    public int gap(IdSegmentChain end, long step) {
        return (int) ((end.getMaxId() - getSequence()) / step);
    }

    public static IdSegmentChain newRoot(boolean allowReset) {
        return new IdSegmentChain(ROOT_VERSION, DefaultIdSegment.OVERFLOW, allowReset);
    }

    @Override
    public long getFetchTime() {
        return idSegment.getFetchTime();
    }

    @Override
    public long getTtl() {
        return idSegment.getTtl();
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
    public long incrementAndGet() {
        return idSegment.incrementAndGet();
    }

    @Override
    public String toString() {
        return "IdSegmentChain{version=" + version + ", idSegment=" + idSegment + '}';
    }
}
