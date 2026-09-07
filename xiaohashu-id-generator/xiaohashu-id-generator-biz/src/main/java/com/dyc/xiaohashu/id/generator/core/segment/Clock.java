package com.dyc.xiaohashu.id.generator.core.segment;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

public interface Clock {

    Clock SYSTEM = new SystemClock();
    Clock CACHE = new CacheClock(SYSTEM);

    long secondTime();

    static long getSystemSecondTime() {
        return System.currentTimeMillis() / 1000;
    }

    class SystemClock implements Clock {

        @Override
        public long secondTime() {
            return getSystemSecondTime();
        }
    }

    class CacheClock implements Clock, Runnable {
        public static final long ONE_SECOND_PERIOD = Duration.ofSeconds(1).toNanos();
        private final Clock clock;
        private final Thread thread;
        private volatile long lastTime;

        public CacheClock(Clock clock) {
            this(clock, true);
        }

        CacheClock(Clock clock, boolean autoStart) {
            this.clock = clock;
            this.lastTime = clock.secondTime();
            this.thread = new Thread(this);
            this.thread.setName("CosId-CacheClock");
            this.thread.setDaemon(true);
            if (autoStart) {
                this.thread.start();
            }
        }

        @Override
        public long secondTime() {
            return lastTime;
        }

        @Override
        public void run() {
            while (!thread.isInterrupted()) {
                tick();
                LockSupport.parkNanos(this, ONE_SECOND_PERIOD);
            }
        }

        void tick() {
            long currentTime = clock.secondTime();
            if (currentTime > lastTime) {
                this.lastTime = currentTime;
            }
        }
    }
}
