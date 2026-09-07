package com.dyc.xiaohashu.id.generator.spring.boot.lifecycle;

import com.dyc.xiaohashu.id.generator.core.machine.InstanceIdentity;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdAllocator;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLeaseConfig;
import com.dyc.xiaohashu.id.generator.core.machine.MachineStatus;
import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;
import com.dyc.xiaohashu.id.generator.core.snowflake.DefaultSnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeConfig;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnowflakeLeaseLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");

    @Test
    void stopShouldReleaseCurrentLease() {
        FakeMachineIdAllocator allocator = new FakeMachineIdAllocator();
        TimeService timeService = () -> NOW;
        DefaultSnowflakeIdGenerator generator = new DefaultSnowflakeIdGenerator(
                SnowflakeConfig.defaults(),
                activeLease(),
                timeService
        );
        SnowflakeLeaseLifecycle lifecycle = new SnowflakeLeaseLifecycle(generator, allocator, MachineLeaseConfig.defaults());

        lifecycle.start();
        lifecycle.stop();

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(allocator.releaseCount()).isEqualTo(1);
        assertThat(lifecycle.releaseFailureTotal()).isZero();
        assertThat(generator.machineLease().status()).isEqualTo(MachineStatus.EXPIRED);
        assertThrows(GeneratorUnavailableException.class, generator::nextId);
    }

    @Test
    void stopShouldRecordReleaseFailureAndKeepStopping() {
        FakeMachineIdAllocator allocator = new FakeMachineIdAllocator();
        allocator.failRelease();
        TimeService timeService = () -> NOW;
        DefaultSnowflakeIdGenerator generator = new DefaultSnowflakeIdGenerator(
                SnowflakeConfig.defaults(),
                activeLease(),
                timeService
        );
        SnowflakeLeaseLifecycle lifecycle = new SnowflakeLeaseLifecycle(generator, allocator, MachineLeaseConfig.defaults());

        lifecycle.start();
        lifecycle.stop();

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(allocator.releaseCount()).isEqualTo(1);
        assertThat(lifecycle.releaseFailureTotal()).isEqualTo(1);
    }

    private static MachineLease activeLease() {
        return new MachineLease(
                "xiaohashu",
                1,
                new InstanceIdentity("test-instance", false),
                MachineStatus.ACTIVE,
                0,
                NOW,
                NOW.plusSeconds(30),
                0
        );
    }

    private static final class FakeMachineIdAllocator implements MachineIdAllocator {

        private final AtomicInteger releaseCount = new AtomicInteger();
        private volatile boolean failRelease;

        @Override
        public MachineLease acquire(String namespace, InstanceIdentity instanceIdentity, int maxMachineId, long lastGeneratedTimestamp) {
            return activeLease();
        }

        @Override
        public MachineLease heartbeat(MachineLease lease, long lastGeneratedTimestamp) {
            return new MachineLease(
                    lease.namespace(),
                    lease.machineId(),
                    lease.instanceIdentity(),
                    MachineStatus.ACTIVE,
                    lastGeneratedTimestamp,
                    NOW,
                    NOW.plusSeconds(30),
                    lease.version() + 1
            );
        }

        @Override
        public void release(MachineLease lease) {
            releaseCount.incrementAndGet();
            if (failRelease) {
                throw new RuntimeException("release failed");
            }
        }

        private int releaseCount() {
            return releaseCount.get();
        }

        private void failRelease() {
            this.failRelease = true;
        }
    }
}
