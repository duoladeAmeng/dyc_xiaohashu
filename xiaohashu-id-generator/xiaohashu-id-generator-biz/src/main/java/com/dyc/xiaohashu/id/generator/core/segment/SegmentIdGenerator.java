package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.IdGenerator;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Copied and modified from CosId's DefaultSegmentId.
 */
public class SegmentIdGenerator implements IdGenerator {

    public static final int ONE_STEP = 1;

    private final long idSegmentTtl;
    private final SegmentAllocator segmentAllocator;
    private final AtomicLong generatedTotal = new AtomicLong();
    private final AtomicLong segmentFetchTotal = new AtomicLong();
    private final AtomicLong segmentFetchFailureTotal = new AtomicLong();
    private final AtomicLong segmentSwitchTotal = new AtomicLong();
    private volatile IdSegment segment = DefaultIdSegment.OVERFLOW;

    public SegmentIdGenerator(SegmentAllocator segmentAllocator) {
        this(IdSegment.TIME_TO_LIVE_FOREVER, segmentAllocator, null);
    }

    public SegmentIdGenerator(long idSegmentTtl, SegmentAllocator segmentAllocator, MeterRegistry meterRegistry) {
        if (idSegmentTtl <= 0) {
            throw new IllegalArgumentException("idSegmentTtl must be greater than 0.");
        }
        this.idSegmentTtl = idSegmentTtl;
        this.segmentAllocator = segmentAllocator;
        registerMetrics(meterRegistry);
    }

    public IdSegment current() {
        return segment;
    }

    @Override
    public long nextId() {
        if (segmentAllocator.getStep() == ONE_STEP) {
            long id = segmentAllocator.nextMaxId();
            generatedTotal.incrementAndGet();
            return id;
        }
        long nextSeq;
        if (segment.isAvailable()) {
            nextSeq = segment.incrementAndGet();
            if (!segment.isOverflow(nextSeq)) {
                generatedTotal.incrementAndGet();
                return nextSeq;
            }
        }

        synchronized (this) {
            while (true) {
                if (segment.isAvailable()) {
                    nextSeq = segment.incrementAndGet();
                    if (!segment.isOverflow(nextSeq)) {
                        generatedTotal.incrementAndGet();
                        return nextSeq;
                    }
                }
                try {
                    IdSegment nextSegment = segmentAllocator.nextIdSegment(idSegmentTtl);
                    segmentFetchTotal.incrementAndGet();
                    if (!segmentAllocator.allowReset()) {
                        segment.ensureNextIdSegment(nextSegment);
                    }
                    segment = nextSegment;
                    segmentSwitchTotal.incrementAndGet();
                } catch (RuntimeException exception) {
                    segmentFetchFailureTotal.incrementAndGet();
                    throw exception;
                }
            }
        }
    }

    private void registerMetrics(MeterRegistry registry) {
        if (registry == null) {
            return;
        }
        Gauge.builder("id_generator_segment_generated_total", generatedTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_segment_fetch_total", segmentFetchTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_segment_fetch_failure_total", segmentFetchFailureTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_segment_switch_total", segmentSwitchTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_segment_current_remaining", this, generator -> Math.max(generator.current().getMaxId() - generator.current().getSequence(), 0L)).register(registry);
    }
}
