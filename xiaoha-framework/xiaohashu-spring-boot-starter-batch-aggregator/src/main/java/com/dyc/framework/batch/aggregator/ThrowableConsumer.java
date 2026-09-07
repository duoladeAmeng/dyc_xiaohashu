package com.dyc.framework.batch.aggregator;

/**
 * 允许抛出受检异常的 consumer。
 *
 * @param <T> 消费对象类型
 * @param <X> 异常类型
 */
@FunctionalInterface
public interface ThrowableConsumer<T, X extends Throwable> {

    void accept(T value) throws X;
}
