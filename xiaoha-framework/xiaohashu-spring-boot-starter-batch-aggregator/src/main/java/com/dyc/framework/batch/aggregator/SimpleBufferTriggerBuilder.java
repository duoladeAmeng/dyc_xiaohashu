package com.dyc.framework.batch.aggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;

/**
 * SimpleBufferTrigger 构造器。
 *
 * @param <E> 入队元素类型
 * @param <C> 缓冲容器类型
 */
@SuppressWarnings("unchecked")
public class SimpleBufferTriggerBuilder<E, C> {

    private static final Logger log = LoggerFactory.getLogger(SimpleBufferTriggerBuilder.class);

    SimpleBufferTrigger.TriggerStrategy triggerStrategy;
    ScheduledExecutorService scheduledExecutorService;
    boolean usingInnerExecutor;
    Supplier<C> bufferFactory;
    ToIntBiFunction<C, E> queueAdder;
    ThrowableConsumer<C, Throwable> consumer;
    BiConsumer<Throwable, C> exceptionHandler;
    LongSupplier maxBufferCount = () -> -1;
    RejectHandler<E> rejectHandler;
    String name;
    boolean disableSwitchLock;

    private boolean maxBufferCountWasSet;

    public <E1, C1> SimpleBufferTriggerBuilder<E1, C1> setContainer(
            Supplier<? extends C1> factory,
            BiPredicate<? super C1, ? super E1> queueAdder) {
        requireNonNull(factory, "factory must not be null");
        requireNonNull(queueAdder, "queueAdder must not be null");

        SimpleBufferTriggerBuilder<E1, C1> thisBuilder = (SimpleBufferTriggerBuilder<E1, C1>) this;
        thisBuilder.bufferFactory = (Supplier<C1>) factory;
        thisBuilder.queueAdder = (container, element) -> queueAdder.test(container, element) ? 1 : 0;
        return thisBuilder;
    }

    public <E1, C1> SimpleBufferTriggerBuilder<E1, C1> setContainerEx(
            Supplier<? extends C1> factory,
            ToIntBiFunction<? super C1, ? super E1> queueAdder) {
        requireNonNull(factory, "factory must not be null");
        requireNonNull(queueAdder, "queueAdder must not be null");

        SimpleBufferTriggerBuilder<E1, C1> thisBuilder = (SimpleBufferTriggerBuilder<E1, C1>) this;
        thisBuilder.bufferFactory = (Supplier<C1>) factory;
        thisBuilder.queueAdder = (ToIntBiFunction<C1, E1>) queueAdder;
        return thisBuilder;
    }

    public SimpleBufferTriggerBuilder<E, C> setScheduleExecutorService(
            ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
        return this;
    }

    public <E1, C1> SimpleBufferTriggerBuilder<E1, C1> setExceptionHandler(
            BiConsumer<? super Throwable, ? super C1> exceptionHandler) {
        SimpleBufferTriggerBuilder<E1, C1> thisBuilder = (SimpleBufferTriggerBuilder<E1, C1>) this;
        thisBuilder.exceptionHandler = (BiConsumer<Throwable, C1>) exceptionHandler;
        return thisBuilder;
    }

    public SimpleBufferTriggerBuilder<E, C> triggerStrategy(SimpleBufferTrigger.TriggerStrategy triggerStrategy) {
        this.triggerStrategy = triggerStrategy;
        return this;
    }

    public SimpleBufferTriggerBuilder<E, C> interval(long interval, TimeUnit unit) {
        if (interval <= 0) {
            throw new IllegalArgumentException("interval must be greater than 0");
        }
        return interval(() -> interval, unit);
    }

    public SimpleBufferTriggerBuilder<E, C> interval(LongSupplier interval, TimeUnit unit) {
        requireNonNull(interval, "interval must not be null");
        requireNonNull(unit, "unit must not be null");

        this.triggerStrategy = (lastConsumeTimestamp, changedCount) -> {
            long intervalInMs = unit.toMillis(interval.getAsLong());
            if (intervalInMs <= 0) {
                throw new IllegalArgumentException("interval must be greater than 0 milliseconds");
            }
            return SimpleBufferTrigger.TriggerResult.trig(
                    changedCount > 0 && System.currentTimeMillis() - lastConsumeTimestamp >= intervalInMs,
                    intervalInMs
            );
        };
        return this;
    }

    public <E1, C1> SimpleBufferTriggerBuilder<E1, C1> consumer(
            ThrowableConsumer<? super C1, Throwable> consumer) {
        requireNonNull(consumer, "consumer must not be null");

        SimpleBufferTriggerBuilder<E1, C1> thisBuilder = (SimpleBufferTriggerBuilder<E1, C1>) this;
        thisBuilder.consumer = (ThrowableConsumer<C1, Throwable>) consumer;
        return thisBuilder;
    }

    public SimpleBufferTriggerBuilder<E, C> maxBufferCount(long count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be greater than 0");
        }
        return maxBufferCount(() -> count);
    }

    public SimpleBufferTriggerBuilder<E, C> maxBufferCount(LongSupplier count) {
        this.maxBufferCount = requireNonNull(count, "count must not be null");
        this.maxBufferCountWasSet = true;
        return this;
    }

    public <E1, C1> SimpleBufferTriggerBuilder<E1, C1> maxBufferCount(
            long count,
            Consumer<? super E1> rejectHandler) {
        return (SimpleBufferTriggerBuilder<E1, C1>) maxBufferCount(count).rejectHandler(rejectHandler);
    }

    public <E1, C1> SimpleBufferTriggerBuilder<E1, C1> rejectHandler(
            Consumer<? super E1> rejectHandler) {
        requireNonNull(rejectHandler, "rejectHandler must not be null");
        return this.rejectHandlerEx((element, ignored) -> {
            rejectHandler.accept(element);
            return false;
        });
    }

    public <E1, C1> SimpleBufferTriggerBuilder<E1, C1> enableBackPressure() {
        return enableBackPressure(null);
    }

    public <E1, C1> SimpleBufferTriggerBuilder<E1, C1> enableBackPressure(BackPressureListener<E1> listener) {
        if (this.rejectHandler != null) {
            throw new IllegalStateException("cannot enable back-pressure while reject handler was set.");
        }
        SimpleBufferTriggerBuilder<E1, C1> thisBuilder = (SimpleBufferTriggerBuilder<E1, C1>) this;
        thisBuilder.rejectHandler = new BackPressureHandler<>(listener);
        return thisBuilder;
    }

    public SimpleBufferTriggerBuilder<E, C> disableSwitchLock() {
        this.disableSwitchLock = true;
        return this;
    }

    public SimpleBufferTriggerBuilder<E, C> name(String name) {
        this.name = name;
        return this;
    }

    public <E1> BufferTrigger<E1> build() {
        check();
        return new LazyBufferTrigger<>(() -> {
            ensure();
            SimpleBufferTriggerBuilder<E1, C> builder = (SimpleBufferTriggerBuilder<E1, C>) this;
            return new SimpleBufferTrigger<>(builder);
        });
    }

    private <E1, C1> SimpleBufferTriggerBuilder<E1, C1> rejectHandlerEx(
            RejectHandler<? super E1> rejectHandler) {
        requireNonNull(rejectHandler, "rejectHandler must not be null");
        if (this.rejectHandler instanceof BackPressureHandler) {
            throw new IllegalStateException("cannot set reject handler while enable back-pressure.");
        }
        SimpleBufferTriggerBuilder<E1, C1> thisBuilder = (SimpleBufferTriggerBuilder<E1, C1>) this;
        thisBuilder.rejectHandler = (RejectHandler<E1>) rejectHandler;
        return thisBuilder;
    }

    private void check() {
        requireNonNull(consumer, "consumer must not be null");
        if (rejectHandler instanceof BackPressureHandler) {
            if (disableSwitchLock) {
                throw new IllegalStateException("back-pressure cannot work together with switch lock disabled.");
            }
            if (!maxBufferCountWasSet) {
                throw new IllegalStateException("back-pressure need to set maxBufferCount.");
            }
        }
    }

    private void ensure() {
        if (triggerStrategy == null) {
            log.warn("no trigger strategy found. using NO-OP trigger.");
            triggerStrategy = (lastConsumeTimestamp, changedCount) -> SimpleBufferTrigger.TriggerResult.empty();
        }

        if (bufferFactory == null && queueAdder == null) {
            log.warn("no container found. use default thread-safe HashSet as container.");
            bufferFactory = () -> (C) Collections.newSetFromMap(new ConcurrentHashMap<>());
            queueAdder = (container, element) -> ((Set<E>) container).add(element) ? 1 : 0;
        }

        if (scheduledExecutorService == null) {
            scheduledExecutorService = makeScheduleExecutor();
            usingInnerExecutor = true;
        }

        if (name != null && rejectHandler instanceof BackPressureHandler) {
            ((BackPressureHandler<E>) rejectHandler).setName(name);
        }
    }

    private ScheduledExecutorService makeScheduleExecutor() {
        String prefix = name == null ? "simple-buffer-trigger" : "simple-buffer-trigger-" + name;
        AtomicInteger threadIndex = new AtomicInteger();
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    private static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
        return value;
    }
}
