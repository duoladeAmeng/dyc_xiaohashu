package com.dyc.framework.batch.aggregator;

import java.util.function.Supplier;

/**
 * 延迟创建真实触发器，避免 builder.build() 后但未使用时就启动后台线程。
 */
class LazyBufferTrigger<E> implements BufferTrigger<E> {

    private final Supplier<BufferTrigger<E>> factory;
    private volatile BufferTrigger<E> delegate;

    LazyBufferTrigger(Supplier<BufferTrigger<E>> factory) {
        this.factory = factory;
    }

    @Override
    public void enqueue(E element) {
        getDelegate().enqueue(element);
    }

    @Override
    public void manuallyDoTrigger() {
        BufferTrigger<E> current = delegate;
        if (current != null) {
            current.manuallyDoTrigger();
        }
    }

    @Override
    public long getPendingChanges() {
        BufferTrigger<E> current = delegate;
        return current == null ? 0 : current.getPendingChanges();
    }

    @Override
    public void close() {
        BufferTrigger<E> current = delegate;
        if (current != null) {
            current.close();
        }
    }

    private BufferTrigger<E> getDelegate() {
        BufferTrigger<E> current = delegate;
        if (current == null) {
            synchronized (this) {
                current = delegate;
                if (current == null) {
                    current = factory.get();
                    delegate = current;
                }
            }
        }
        return current;
    }
}
