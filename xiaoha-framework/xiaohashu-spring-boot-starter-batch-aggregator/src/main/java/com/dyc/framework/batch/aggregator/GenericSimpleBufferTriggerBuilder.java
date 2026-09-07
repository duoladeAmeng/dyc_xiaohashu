package com.dyc.framework.batch.aggregator;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;

/**
 * SimpleBufferTrigger 的类型安全包装 builder。
 *
 * @param <E> 入队元素类型
 * @param <C> 缓冲容器类型
 */
public class GenericSimpleBufferTriggerBuilder<E, C> {

    private final SimpleBufferTriggerBuilder<Object, Object> builder;

    GenericSimpleBufferTriggerBuilder(SimpleBufferTriggerBuilder<Object, Object> builder) {
        this.builder = builder;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> setContainer(
            Supplier<? extends C> factory,
            BiPredicate<? super C, ? super E> queueAdder) {
        builder.setContainer(factory, queueAdder);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> setContainerEx(
            Supplier<? extends C> factory,
            ToIntBiFunction<? super C, ? super E> queueAdder) {
        builder.setContainerEx(factory, queueAdder);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> setScheduleExecutorService(
            ScheduledExecutorService scheduledExecutorService) {
        builder.setScheduleExecutorService(scheduledExecutorService);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> setExceptionHandler(
            BiConsumer<? super Throwable, ? super C> exceptionHandler) {
        builder.setExceptionHandler(exceptionHandler);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> triggerStrategy(
            SimpleBufferTrigger.TriggerStrategy triggerStrategy) {
        builder.triggerStrategy(triggerStrategy);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> interval(long interval, TimeUnit unit) {
        builder.interval(interval, unit);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> interval(LongSupplier interval, TimeUnit unit) {
        builder.interval(interval, unit);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> consumer(
            ThrowableConsumer<? super C, Throwable> consumer) {
        builder.consumer(consumer);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> maxBufferCount(long count) {
        builder.maxBufferCount(count);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> maxBufferCount(LongSupplier count) {
        builder.maxBufferCount(count);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> maxBufferCount(
            long count,
            Consumer<? super E> rejectHandler) {
        builder.maxBufferCount(count, rejectHandler);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> rejectHandler(Consumer<? super E> rejectHandler) {
        builder.rejectHandler(rejectHandler);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> enableBackPressure() {
        builder.enableBackPressure();
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> enableBackPressure(BackPressureListener<E> listener) {
        builder.enableBackPressure(listener);
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> disableSwitchLock() {
        builder.disableSwitchLock();
        return this;
    }

    public GenericSimpleBufferTriggerBuilder<E, C> name(String name) {
        builder.name(name);
        return this;
    }

    public BufferTrigger<E> build() {
        return builder.build();
    }
}
