package com.dyc.framework.batch.aggregator;

/**
 * 本地缓冲达到 maxBufferCount 后进入背压等待时的回调。
 *
 * @param <T> 入队元素类型
 */
@FunctionalInterface
public interface BackPressureListener<T> {

    void onHandle(T element);
}
