package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Plain segment ID generator that fetches a new segment only after the current segment is exhausted.
 */
public class DefaultSegmentIdGenerator implements SegmentIdGenerator {

    private final String namespace;
    private final String tag;
    private final SegmentIdConfig config;
    private final SegmentAllocator allocator;
    private final AtomicReference<IdSegment> currentSegment;
    private final LongAdder generatedTotal = new LongAdder();
    private final LongAdder segmentAllocatedTotal = new LongAdder();
    private final LongAdder segmentFetchFailureTotal = new LongAdder();

    public DefaultSegmentIdGenerator(String namespace, String tag, SegmentIdConfig config, SegmentAllocator allocator) {
        this.namespace = requireText(namespace, "namespace");
        this.tag = requireText(tag, "tag");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.allocator = Objects.requireNonNull(allocator, "allocator must not be null");
        this.currentSegment = new AtomicReference<>(nextSegment());
    }

    @Override
    public long nextId() {
        while (true) {
            IdSegment segment = currentSegment.get();
            long id = segment.tryNextId();
            if (id != IdSegment.SEQUENCE_OVERFLOW) {
                generatedTotal.increment();
                return id;
            }
            switchSegment(segment);
        }
    }

    @Override
    public IdSegment currentSegment() {
        return currentSegment.get();
    }

    @Override
    public long generatedTotal() {
        return generatedTotal.sum();
    }

    @Override
    public long segmentAllocatedTotal() {
        return segmentAllocatedTotal.sum();
    }

    @Override
    public long segmentFetchFailureTotal() {
        return segmentFetchFailureTotal.sum();
    }

    private void switchSegment(IdSegment exhaustedSegment) {
        synchronized (this) {
            if (currentSegment.get() == exhaustedSegment) {
                currentSegment.set(nextSegment());
            }
        }
    }

    private IdSegment nextSegment() {
        try {
            IdSegment segment = allocator.nextSegment(namespace, tag, config.defaultStep());
            segmentAllocatedTotal.increment();
            return segment;
        } catch (RuntimeException exception) {
            segmentFetchFailureTotal.increment();
            throw exception;
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new InvalidIdGeneratorConfigurationException(name + " must not be blank");
        }
        return value;
    }
}
