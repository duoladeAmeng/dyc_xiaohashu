package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.IdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.concurrent.AffinityJob;
import com.dyc.xiaohashu.id.generator.core.segment.concurrent.PrefetchWorker;
import com.dyc.xiaohashu.id.generator.core.segment.concurrent.PrefetchWorkerExecutorService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Copied and modified from CosId's SegmentChainId.
 */
public class SegmentChainIdGenerator implements IdGenerator {

    private static final Logger log = LoggerFactory.getLogger(SegmentChainIdGenerator.class);
    public static final int DEFAULT_SAFE_DISTANCE = 2;

    private final long idSegmentTtl;
    private final int safeDistance;
    private final SegmentAllocator segmentAllocator;
    private final PrefetchJob prefetchJob;
    private final AtomicLong generatedTotal = new AtomicLong();
    private final AtomicLong segmentPrefetchTotal = new AtomicLong();
    private final AtomicLong segmentPrefetchFailureTotal = new AtomicLong();
    private final AtomicLong segmentSwitchTotal = new AtomicLong();
    private final AtomicLong hungerTotal = new AtomicLong();
    private volatile IdSegmentChain headChain;

    public SegmentChainIdGenerator(SegmentAllocator segmentAllocator) {
        this(IdSegment.TIME_TO_LIVE_FOREVER, DEFAULT_SAFE_DISTANCE, segmentAllocator,
                new PrefetchWorkerExecutorService(PrefetchWorkerExecutorService.DEFAULT_PREFETCH_PERIOD, Runtime.getRuntime().availableProcessors()), null);
    }

    public SegmentChainIdGenerator(long idSegmentTtl,
                                   int safeDistance,
                                   SegmentAllocator segmentAllocator,
                                   PrefetchWorkerExecutorService prefetchWorkerExecutorService,
                                   MeterRegistry meterRegistry) {
        if (idSegmentTtl <= 0) {
            throw new IllegalArgumentException("idSegmentTtl must be greater than 0.");
        }
        if (safeDistance <= 0) {
            throw new IllegalArgumentException("safeDistance must be greater than 0.");
        }
        this.headChain = IdSegmentChain.newRoot(segmentAllocator.allowReset());
        this.idSegmentTtl = idSegmentTtl;
        this.safeDistance = safeDistance;
        this.segmentAllocator = segmentAllocator;
        this.prefetchJob = new PrefetchJob(headChain);
        registerMetrics(meterRegistry);
        prefetchWorkerExecutorService.submit(prefetchJob);
    }

    public IdSegment current() {
        return headChain;
    }

    public IdSegmentChain getHead() {
        return headChain;
    }

    @Override
    public long nextId() {
        while (true) {
            IdSegmentChain currentChain = headChain;
            while (currentChain != null) {
                if (currentChain.isAvailable()) {
                    long nextSeq = currentChain.incrementAndGet();
                    if (!currentChain.isOverflow(nextSeq)) {
                        forward(currentChain);
                        generatedTotal.incrementAndGet();
                        return nextSeq;
                    }
                }
                currentChain = currentChain.getNext();
            }

            try {
                IdSegmentChain preHead = headChain;
                if (preHead.trySetNext(preChain -> generateNext(preChain, safeDistance))) {
                    IdSegmentChain nextChain = preHead.getNext();
                    forward(nextChain);
                }
            } catch (NextIdSegmentExpiredException exception) {
                log.warn("Generate {} gave up expired next IdSegmentChain.", segmentAllocator.getNamespacedName(), exception);
            } catch (RuntimeException exception) {
                segmentPrefetchFailureTotal.incrementAndGet();
                log.warn("Generate {} failed to fetch next IdSegmentChain.", segmentAllocator.getNamespacedName(), exception);
            }
            this.prefetchJob.hungry();
            hungerTotal.incrementAndGet();
        }
    }

    private void forward(IdSegmentChain forwardChain) {
        if (forwardChain == null || headChain.getVersion() >= forwardChain.getVersion()) {
            return;
        }
        if (segmentAllocator.allowReset()) {
            headChain = forwardChain;
            segmentSwitchTotal.incrementAndGet();
        } else if (forwardChain.compareTo(headChain) > 0) {
            headChain = forwardChain;
            segmentSwitchTotal.incrementAndGet();
        }
    }

    private IdSegmentChain generateNext(IdSegmentChain previousChain, int segments) {
        IdSegmentChain chain = segmentAllocator.nextIdSegmentChain(previousChain, segments, idSegmentTtl);
        segmentPrefetchTotal.incrementAndGet();
        return chain;
    }

    public int getSafeDistance() {
        return safeDistance;
    }

    private void registerMetrics(MeterRegistry registry) {
        if (registry == null) {
            return;
        }
        Gauge.builder("id_generator_segment_chain_generated_total", generatedTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_segment_chain_prefetch_total", segmentPrefetchTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_segment_chain_prefetch_failure_total", segmentPrefetchFailureTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_segment_chain_switch_total", segmentSwitchTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_segment_chain_hunger_total", hungerTotal, AtomicLong::get).register(registry);
        Gauge.builder("id_generator_segment_chain_safe_distance", this, SegmentChainIdGenerator::getSafeDistance).register(registry);
    }

    public class PrefetchJob implements AffinityJob {

        private static final int MAX_PREFETCH_DISTANCE = 100_000_000;
        private static final long HUNGER_THRESHOLD_SECONDS = 5;

        private volatile PrefetchWorker prefetchWorker;
        private int prefetchDistance = safeDistance;
        private IdSegmentChain tailChain;
        private volatile long lastHungerTime;

        public PrefetchJob(IdSegmentChain tailChain) {
            this.tailChain = tailChain;
        }

        @Override
        public String getJobId() {
            return segmentAllocator.getNamespacedName();
        }

        @Override
        public void setHungerTime(long hungerTime) {
            lastHungerTime = hungerTime;
        }

        @Override
        public PrefetchWorker getPrefetchWorker() {
            return prefetchWorker;
        }

        @Override
        public void setPrefetchWorker(PrefetchWorker prefetchWorker) {
            if (this.prefetchWorker == null) {
                this.prefetchWorker = prefetchWorker;
            }
        }

        @Override
        public void run() {
            prefetch();
        }

        public void prefetch() {
            long wakeupTimeGap = Clock.secondTime() - lastHungerTime;
            boolean hunger = wakeupTimeGap < HUNGER_THRESHOLD_SECONDS;
            int prePrefetchDistance = this.prefetchDistance;
            if (hunger) {
                this.prefetchDistance = Math.min(Math.multiplyExact(this.prefetchDistance, 2), MAX_PREFETCH_DISTANCE);
            } else {
                this.prefetchDistance = Math.max(Math.floorDiv(this.prefetchDistance, 2), safeDistance);
            }

            IdSegmentChain availableHeadChain = SegmentChainIdGenerator.this.headChain;
            while (!availableHeadChain.getIdSegment().isAvailable()) {
                availableHeadChain = availableHeadChain.getNext();
                if (availableHeadChain == null) {
                    availableHeadChain = tailChain;
                    break;
                }
            }

            forward(availableHeadChain);
            int headToTailGap = availableHeadChain.gap(tailChain, segmentAllocator.getStep());
            int safeGap = safeDistance - headToTailGap;
            if (safeGap <= 0 && !hunger) {
                return;
            }
            int prefetchSegments = hunger ? this.prefetchDistance : safeGap;
            if (prePrefetchDistance != this.prefetchDistance) {
                log.info("Prefetch {} safeDistance changed [{} -> {}].", segmentAllocator.getNamespacedName(), prePrefetchDistance, this.prefetchDistance);
            }
            appendChain(prefetchSegments);
        }

        private void appendChain(int prefetchSegments) {
            try {
                tailChain = tailChain.ensureSetNext(preChain -> generateNext(preChain, prefetchSegments)).getNext();
                while (tailChain.getNext() != null) {
                    tailChain = tailChain.getNext();
                }
            } catch (NextIdSegmentExpiredException exception) {
                segmentPrefetchFailureTotal.incrementAndGet();
                log.warn("AppendChain {} gave up expired next IdSegmentChain.", segmentAllocator.getNamespacedName(), exception);
            } catch (RuntimeException exception) {
                segmentPrefetchFailureTotal.incrementAndGet();
                log.warn("AppendChain {} failed.", segmentAllocator.getNamespacedName(), exception);
            }
        }
    }
}
