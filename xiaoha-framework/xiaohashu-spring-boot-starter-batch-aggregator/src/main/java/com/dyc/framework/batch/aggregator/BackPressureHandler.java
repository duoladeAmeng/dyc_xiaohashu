package com.dyc.framework.batch.aggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Condition;

class BackPressureHandler<T> implements RejectHandler<T> {

    private static final Logger log = LoggerFactory.getLogger(BackPressureHandler.class);

    private static volatile GlobalBackPressureListener globalBackPressureListener;

    private final BackPressureListener<T> listener;
    private String name;

    BackPressureHandler(BackPressureListener<T> listener) {
        this.listener = listener;
    }

    void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean onReject(T element, Condition condition) {
        if (listener != null) {
            try {
                listener.onHandle(element);
            } catch (Throwable e) {
                log.error("back-pressure listener failed.", e);
            }
        }
        if (globalBackPressureListener != null) {
            try {
                globalBackPressureListener.onHandle(name, element);
            } catch (Throwable e) {
                log.error("global back-pressure listener failed.", e);
            }
        }

        long startNano = System.nanoTime();
        condition.awaitUninterruptibly();
        long blockInNano = System.nanoTime() - startNano;

        if (globalBackPressureListener != null) {
            try {
                globalBackPressureListener.postHandle(name, element, blockInNano);
            } catch (Throwable e) {
                log.error("global back-pressure post listener failed.", e);
            }
        }
        return true;
    }

    static void setupGlobalBackPressureListener(GlobalBackPressureListener listener) {
        globalBackPressureListener = listener;
    }
}
