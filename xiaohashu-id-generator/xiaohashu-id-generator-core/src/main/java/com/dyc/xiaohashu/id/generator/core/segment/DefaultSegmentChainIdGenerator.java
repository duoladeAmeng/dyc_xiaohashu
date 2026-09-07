package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;
import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Segment-chain generator with background prefetch and synchronous hunger fallback.
 */
public class DefaultSegmentChainIdGenerator implements SegmentChainIdGenerator, AutoCloseable {

    private static final long NANOS_PER_MILLIS = 1_000_000L;

    private final String namespace;
    private final String tag;
    private final SegmentIdConfig segmentConfig;
    private final SegmentChainConfig chainConfig;
    private final SegmentAllocator allocator;
    private final SegmentPrefetchScheduler scheduler;
    private final String jobId;
    private final AtomicReference<SegmentChainNode> head;
    private final AtomicReference<SegmentChainNode> tail;
    private final AtomicLong nextVersion = new AtomicLong();
    private final AtomicBoolean hungry = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean synchronousAppendInFlight = new AtomicBoolean();
    private final AtomicInteger prefetchDistance;
    private final AtomicLong generatedTotal = new AtomicLong();
    private final AtomicLong segmentAllocatedTotal = new AtomicLong();
    private final AtomicLong segmentFetchFailureTotal = new AtomicLong();
    private final AtomicLong prefetchSuccessTotal = new AtomicLong();
    private final AtomicLong prefetchFailureTotal = new AtomicLong();

    public DefaultSegmentChainIdGenerator(
            String namespace,
            String tag,
            SegmentIdConfig segmentConfig,
            SegmentChainConfig chainConfig,
            SegmentAllocator allocator,
            SegmentPrefetchScheduler scheduler
    ) {
        this.namespace = requireText(namespace, "namespace");
        this.tag = requireText(tag, "tag");
        this.segmentConfig = Objects.requireNonNull(segmentConfig, "segmentConfig must not be null");
        this.chainConfig = Objects.requireNonNull(chainConfig, "chainConfig must not be null");
        this.allocator = Objects.requireNonNull(allocator, "allocator must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        IdSegment firstSegment = nextSegment();
        SegmentChainNode firstNode = new SegmentChainNode(nextVersion.getAndIncrement(), firstSegment);
        this.head = new AtomicReference<>(firstNode);
        this.tail = new AtomicReference<>(firstNode);
        this.prefetchDistance = new AtomicInteger(this.chainConfig.safeDistance());
        this.jobId = this.namespace + "." + this.tag + ".segment-chain";
        this.scheduler.register(jobId, this::prefetch);
        prefetchToDistance(this.chainConfig.safeDistance());
    }

    @Override
    public long nextId() {
        if (closed.get()) {
            throw new GeneratorUnavailableException("segment-chain generator is closed");
        }
        while (true) {
            SegmentChainNode node = head.get();
            while (node != null) {
                long id = node.segment().tryNextId();
                if (id != IdSegment.SEQUENCE_OVERFLOW) {
                    forward(node);
                    if (distanceToTail(node) < chainConfig.safeDistance()) {
                        scheduler.wakeup(jobId);
                    }
                    generatedTotal.incrementAndGet();
                    return id;
                }
                SegmentChainNode next = node.next();
                if (next == null) {
                    break;
                }
                forward(next);
                node = next;
            }
            hungry.set(true);
            scheduler.wakeup(jobId);
            appendSynchronouslyOnce();
        }
    }

    @Override
    public IdSegment currentSegment() {
        return head.get().segment();
    }

    @Override
    public SegmentChainNode head() {
        return head.get();
    }

    /**
     * Returns the current tail node. Intended for diagnostics and tests.
     *
     * @return chain tail
     */
    public SegmentChainNode tail() {
        return tail.get();
    }

    /**
     * Returns the adaptive prefetch distance in segments.
     *
     * @return prefetch distance
     */
    public int prefetchDistance() {
        return prefetchDistance.get();
    }

    @Override
    public int chainDistance() {
        return distance(head.get(), tail.get());
    }

    @Override
    public boolean closed() {
        return closed.get();
    }

    @Override
    public long generatedTotal() {
        return generatedTotal.get();
    }

    @Override
    public long segmentAllocatedTotal() {
        return segmentAllocatedTotal.get();
    }

    @Override
    public long segmentFetchFailureTotal() {
        return segmentFetchFailureTotal.get();
    }

    @Override
    public long prefetchSuccessTotal() {
        return prefetchSuccessTotal.get();
    }

    @Override
    public long prefetchFailureTotal() {
        return prefetchFailureTotal.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            scheduler.unregister(jobId);
        }
    }

    private void prefetch() {
        if (closed.get()) {
            return;
        }
        try {
            boolean wasHungry = hungry.getAndSet(false);
            int targetDistance = adjustPrefetchDistance(wasHungry);
            if (!wasHungry) {
                targetDistance = chainConfig.safeDistance();
            }
            prefetchToDistance(targetDistance);
            prefetchSuccessTotal.incrementAndGet();
        } catch (RuntimeException exception) {
            prefetchFailureTotal.incrementAndGet();
            throw exception;
        }
    }

    private int adjustPrefetchDistance(boolean wasHungry) {
        while (true) {
            int current = prefetchDistance.get();
            int next = wasHungry
                    ? Math.min(current << 1, chainConfig.maxPrefetchDistance())
                    : Math.max(current / 2, chainConfig.safeDistance());
            if (prefetchDistance.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    private void prefetchToDistance(int targetDistance) {
        while (!closed.get()) {
            SegmentChainNode currentHead = forwardToAvailableHead();
            int gap = distance(currentHead, tail.get());
            int missing = targetDistance - gap;
            if (missing <= 0) {
                return;
            }
            appendOneWithRetry();
        }
    }

    private SegmentChainNode appendOneWithRetry() {
        RuntimeException lastException = null;
        for (int attempt = 0; attempt <= chainConfig.prefetchRetryCount(); attempt++) {
            try {
                return appendOne();
            } catch (RuntimeException exception) {
                lastException = exception;
                if (attempt == chainConfig.prefetchRetryCount()) {
                    throw exception;
                }
                LockSupport.parkNanos(chainConfig.prefetchRetryBackoff().toMillis() * NANOS_PER_MILLIS);
            }
        }
        throw lastException;
    }

    private void appendSynchronouslyOnce() {
        if (!synchronousAppendInFlight.compareAndSet(false, true)) {
            Thread.yield();
            return;
        }
        try {
            appendOneWithRetry();
        } finally {
            synchronousAppendInFlight.set(false);
        }
    }

    private SegmentChainNode appendOne() {
        while (true) {
            SegmentChainNode observedTail = tail.get();
            SegmentChainNode next = observedTail.next();
            if (next != null) {
                moveTail(next);
                continue;
            }
            AtomicReference<SegmentChainNode> installed = new AtomicReference<>();
            boolean success = observedTail.trySetNext(() -> {
                IdSegment nextSegment = nextSegment();
                SegmentChainNode candidate = new SegmentChainNode(nextVersion.getAndIncrement(), nextSegment);
                installed.set(candidate);
                return candidate;
            });
            if (success) {
                SegmentChainNode nextNode = installed.get();
                moveTail(nextNode);
                return nextNode;
            }
        }
    }

    private SegmentChainNode forwardToAvailableHead() {
        SegmentChainNode node = head.get();
        while (node.next() != null && !node.segment().isAvailable()) {
            node = node.next();
        }
        forward(node);
        return head.get();
    }

    private void forward(SegmentChainNode candidate) {
        while (true) {
            SegmentChainNode current = head.get();
            if (candidate.version() <= current.version()) {
                return;
            }
            if (head.compareAndSet(current, candidate)) {
                return;
            }
        }
    }

    private void moveTail(SegmentChainNode candidate) {
        while (true) {
            SegmentChainNode current = tail.get();
            if (candidate.version() <= current.version()) {
                return;
            }
            if (tail.compareAndSet(current, candidate)) {
                return;
            }
        }
    }

    private int distanceToTail(SegmentChainNode start) {
        return distance(start, tail.get());
    }

    private int distance(SegmentChainNode start, SegmentChainNode end) {
        if (end.version() < start.version()) {
            return 0;
        }
        long distance = end.version() - start.version();
        return distance > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) distance;
    }

    private IdSegment nextSegment() {
        try {
            IdSegment segment = allocator.nextSegment(namespace, tag, segmentConfig.defaultStep());
            segmentAllocatedTotal.incrementAndGet();
            return segment;
        } catch (RuntimeException exception) {
            segmentFetchFailureTotal.incrementAndGet();
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
