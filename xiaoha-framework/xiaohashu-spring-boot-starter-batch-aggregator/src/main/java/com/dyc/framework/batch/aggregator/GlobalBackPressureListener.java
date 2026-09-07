package com.dyc.framework.batch.aggregator;

/**
 * 全局背压监听器，便于统一上报阻塞耗时。
 */
public interface GlobalBackPressureListener {

    void onHandle(String name, Object element);

    void postHandle(String name, Object element, long blockInNano);
}
