package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe local ID segment represented by a closed interval.
 */
public final class IdSegment {

    public static final long SEQUENCE_OVERFLOW = -1L;

    private final String namespace;
    private final String tag;
    private final long startInclusive;
    private final long endInclusive;
    private final AtomicLong cursor;

    public IdSegment(String namespace, String tag, long startInclusive, long endInclusive) {
        requireText(namespace, "namespace");
        requireText(tag, "tag");
        if (startInclusive <= 0) {
            throw new InvalidIdGeneratorConfigurationException("startInclusive must be greater than zero");
        }
        if (endInclusive < startInclusive) {
            throw new InvalidIdGeneratorConfigurationException("endInclusive must not be less than startInclusive");
        }
        this.namespace = namespace;
        this.tag = tag;
        this.startInclusive = startInclusive;
        this.endInclusive = endInclusive;
        this.cursor = new AtomicLong(startInclusive - 1);
    }

    /**
     * Returns the next ID or {@link #SEQUENCE_OVERFLOW} when exhausted.
     *
     * @return next ID or overflow marker
     */
    public long tryNextId() {
        long next = cursor.incrementAndGet();
        if (next > endInclusive) {
            return SEQUENCE_OVERFLOW;
        }
        return next;
    }

    /**
     * Returns whether this segment still has local IDs.
     *
     * @return true when more IDs are available
     */
    public boolean isAvailable() {
        return cursor.get() < endInclusive;
    }

    /**
     * Returns remaining local IDs.
     *
     * @return remaining IDs
     */
    public long remaining() {
        return Math.max(0L, endInclusive - cursor.get());
    }

    /**
     * Returns the namespace.
     *
     * @return namespace
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Returns the business tag.
     *
     * @return tag
     */
    public String tag() {
        return tag;
    }

    /**
     * Returns the first ID in this segment.
     *
     * @return first ID
     */
    public long startInclusive() {
        return startInclusive;
    }

    /**
     * Returns the last ID in this segment.
     *
     * @return last ID
     */
    public long endInclusive() {
        return endInclusive;
    }

    /**
     * Returns the number of IDs reserved in this segment.
     *
     * @return segment size
     */
    public long size() {
        return endInclusive - startInclusive + 1;
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new InvalidIdGeneratorConfigurationException(name + " must not be blank");
        }
    }
}
