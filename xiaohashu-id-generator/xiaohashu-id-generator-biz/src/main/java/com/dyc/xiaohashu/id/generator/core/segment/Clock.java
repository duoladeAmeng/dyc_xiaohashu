package com.dyc.xiaohashu.id.generator.core.segment;

final class Clock {

    private Clock() {
    }

    static long secondTime() {
        return System.currentTimeMillis() / 1000;
    }
}
