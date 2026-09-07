package com.dyc.framework.batch.aggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;

/**
 * SimpleBufferTrigger 的核心实现。
 *
 * <p>写入路径只负责把元素追加/合并到内存容器；后台单线程定时判断是否满足触发条件，满足后切换容器并批量消费旧容器。</p>
 *
 * @param <E> 入队元素类型
 * @param <C> 缓冲容器类型
 */
public class SimpleBufferTrigger<E, C> implements BufferTrigger<E> {

    private static final Logger log = LoggerFactory.getLogger(SimpleBufferTrigger.class);
    private static final long DEFAULT_NEXT_TRIGGER_PERIOD = TimeUnit.SECONDS.toMillis(1);

    private final AtomicLong counter = new AtomicLong();
    private final ThrowableConsumer<C, Throwable> consumer;
    private final ToIntBiFunction<C, E> queueAdder;
    private final Supplier<C> bufferFactory;
    private final BiConsumer<Throwable, C> exceptionHandler;
    private final AtomicReference<C> buffer = new AtomicReference<>();
    private final LongSupplier maxBufferCount;
    private final RejectHandler<E> rejectHandler;
    private final ReentrantReadWriteLock.ReadLock readLock;
    private final ReentrantReadWriteLock.WriteLock writeLock;
    private final Condition writeCondition;
    private final Runnable shutdownExecutor;

    private volatile boolean shutdown;
    private volatile long lastConsumeTimestamp = System.currentTimeMillis();

    SimpleBufferTrigger(SimpleBufferTriggerBuilder<E, C> builder) {
        this.queueAdder = builder.queueAdder;
        this.bufferFactory = builder.bufferFactory;
        this.consumer = builder.consumer;
        this.exceptionHandler = builder.exceptionHandler;
        this.maxBufferCount = builder.maxBufferCount;
        this.rejectHandler = builder.rejectHandler;
        this.buffer.set(this.bufferFactory.get());

        if (builder.disableSwitchLock) {
            this.readLock = null;
            this.writeLock = null;
            this.writeCondition = null;
        } else {
            ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
            this.readLock = lock.readLock();
            this.writeLock = lock.writeLock();
            this.writeCondition = this.writeLock.newCondition();
        }

        builder.scheduledExecutorService.schedule(
                new TriggerRunnable(builder.scheduledExecutorService, builder.triggerStrategy),
                DEFAULT_NEXT_TRIGGER_PERIOD,
                TimeUnit.MILLISECONDS
        );
        this.shutdownExecutor = () -> {
            if (builder.usingInnerExecutor) {
                shutdownAndAwaitTermination(builder.scheduledExecutorService);
            }
        };
    }

    public static <E, C> SimpleBufferTriggerBuilder<E, C> newBuilder() {
        return new SimpleBufferTriggerBuilder<>();
    }

    /**
     * 快速创建一个以 Map 计数的聚合容器，适合把同一 key 的多次事件累加为 delta。
     */
    public static <E> SimpleBufferTriggerBuilder<E, Map<E, Integer>> newCounterBuilder() {
        return new SimpleBufferTriggerBuilder<E, Map<E, Integer>>()
                .setContainerEx(ConcurrentHashMap::new, (map, element) -> {
                    map.merge(element, 1, Math::addExact);
                    return 1;
                });
    }

    @Override
    public void enqueue(E element) {
        if (shutdown) {
            throw new IllegalStateException("buffer trigger was shutdown.");
        }

        long currentCount = counter.get();
        long thisMaxBufferCount = maxBufferCount.getAsLong();
        if (thisMaxBufferCount > 0 && currentCount >= thisMaxBufferCount) {
            boolean pass = true;
            if (rejectHandler != null) {
                if (writeLock != null && writeCondition != null) {
                    writeLock.lock();
                }
                try {
                    currentCount = counter.get();
                    thisMaxBufferCount = maxBufferCount.getAsLong();
                    if (thisMaxBufferCount > 0 && currentCount >= thisMaxBufferCount) {
                        pass = fireRejectHandler(element);
                    }
                } finally {
                    if (writeLock != null && writeCondition != null) {
                        writeLock.unlock();
                    }
                }
            }
            if (!pass) {
                return;
            }
        }

        boolean locked = false;
        if (readLock != null) {
            readLock.lock();
            locked = true;
        }
        try {
            C currentBuffer = buffer.get();
            int changedCount = queueAdder.applyAsInt(currentBuffer, element);
            if (changedCount > 0) {
                counter.addAndGet(changedCount);
            }
        } finally {
            if (locked) {
                readLock.unlock();
            }
        }
    }

    private boolean fireRejectHandler(E element) {
        try {
            return rejectHandler.onReject(element, writeCondition);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void manuallyDoTrigger() {
        synchronized (this) {
            doConsume();
        }
    }

    private void doConsume() {
        C old = null;
        boolean hasChanges = false;
        try {
            if (writeLock != null) {
                writeLock.lock();
            }
            try {
                hasChanges = counter.get() > 0;
                if (hasChanges) {
                    old = buffer.getAndSet(bufferFactory.get());
                }
            } finally {
                counter.set(0);
                if (writeCondition != null) {
                    writeCondition.signalAll();
                }
                if (writeLock != null) {
                    writeLock.unlock();
                }
            }
            if (hasChanges && old != null) {
                consumer.accept(old);
            }
        } catch (Throwable e) {
            if (exceptionHandler != null) {
                handleConsumerException(e, old);
            } else {
                log.error("consume buffer failed.", e);
            }
        }
    }

    private void handleConsumerException(Throwable e, C old) {
        try {
            exceptionHandler.accept(e, old);
        } catch (Throwable handlerError) {
            log.error("buffer exception handler failed.", handlerError);
            log.error("original buffer consume exception.", e);
        }
    }

    @Override
    public long getPendingChanges() {
        return counter.get();
    }

    @Override
    public void close() {
        shutdown = true;
        try {
            manuallyDoTrigger();
        } finally {
            shutdownExecutor.run();
        }
    }

    public static void setupGlobalBackPressure(GlobalBackPressureListener listener) {
        BackPressureHandler.setupGlobalBackPressureListener(listener);
    }

    private static void shutdownAndAwaitTermination(ScheduledExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 消费触发策略。
     */
    public interface TriggerStrategy {

        TriggerResult canTrigger(long lastConsumeTimestamp, long changedCount);
    }

    /**
     * 触发策略返回值。
     */
    public static class TriggerResult {

        private static final TriggerResult EMPTY = new TriggerResult(false, TimeUnit.DAYS.toMillis(1));

        private final boolean doConsumer;
        private final long nextPeriod;

        private TriggerResult(boolean doConsumer, long nextPeriod) {
            this.doConsumer = doConsumer;
            this.nextPeriod = nextPeriod;
        }

        public static TriggerResult trig(boolean doConsumer, long nextPeriod) {
            return new TriggerResult(doConsumer, nextPeriod);
        }

        public static TriggerResult empty() {
            return EMPTY;
        }
    }

    private class TriggerRunnable implements Runnable {

        private final ScheduledExecutorService scheduledExecutorService;
        private final TriggerStrategy triggerStrategy;

        TriggerRunnable(ScheduledExecutorService scheduledExecutorService, TriggerStrategy triggerStrategy) {
            this.scheduledExecutorService = scheduledExecutorService;
            this.triggerStrategy = triggerStrategy;
        }

        @Override
        public void run() {
            synchronized (SimpleBufferTrigger.this) {
                long nextTriggerPeriod = DEFAULT_NEXT_TRIGGER_PERIOD;
                try {
                    TriggerResult triggerResult = triggerStrategy.canTrigger(lastConsumeTimestamp, counter.get());
                    nextTriggerPeriod = triggerResult.nextPeriod;
                    long beforeConsume = System.currentTimeMillis();
                    if (triggerResult.doConsumer) {
                        lastConsumeTimestamp = beforeConsume;
                        doConsume();
                    }
                    nextTriggerPeriod -= System.currentTimeMillis() - beforeConsume;
                } catch (Throwable e) {
                    log.error("execute buffer trigger failed.", e);
                }

                nextTriggerPeriod = Math.max(0, nextTriggerPeriod);
                if (!shutdown) {
                    scheduledExecutorService.schedule(this, nextTriggerPeriod, TimeUnit.MILLISECONDS);
                }
            }
        }
    }
}
