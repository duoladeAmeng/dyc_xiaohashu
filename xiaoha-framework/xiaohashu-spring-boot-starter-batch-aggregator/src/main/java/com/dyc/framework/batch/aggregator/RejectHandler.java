package com.dyc.framework.batch.aggregator;

import java.util.concurrent.locks.Condition;

interface RejectHandler<T> {

    /**
     * @return true 表示继续入队，false 表示丢弃本次元素
     */
    boolean onReject(T element, Condition condition) throws Throwable;
}
