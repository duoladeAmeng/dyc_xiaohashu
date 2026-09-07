package com.dyc.framework.batch.aggregator;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地批量聚合触发器。
 *
 * <p>适用于点赞、收藏、评论数等允许短时间最终一致的高频计数场景：先把请求线程中的单次变更写入本地缓冲，
 * 再由后台定时任务批量消费，降低 MQ/DB/Redis 的写入频率。</p>
 *
 * @param <E> 入队元素类型
 */
public interface BufferTrigger<E> extends AutoCloseable {

    /**
     * 将一个变更事件写入缓冲区。
     *
     * @throws IllegalStateException 触发器已关闭时抛出
     */
    void enqueue(E element);

    /**
     * 手动触发一次消费，通常用于应用关闭前兜底刷盘。
     */
    void manuallyDoTrigger();

    /**
     * 返回当前尚未消费的变更计数。
     */
    long getPendingChanges();

    /**
     * 创建 SimpleBufferTrigger 的通用 builder。
     *
     * @param <E> 入队元素类型
     * @param <C> 缓冲容器类型。默认容器为 {@link ConcurrentHashMap#newKeySet()}，可视作 {@link Set}
     */
    static <E, C> GenericSimpleBufferTriggerBuilder<E, C> simple() {
        return new GenericSimpleBufferTriggerBuilder<>(SimpleBufferTrigger.newBuilder());
    }

    @Override
    void close();
}
