package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.machine.InstanceIdentity;
import com.dyc.xiaohashu.id.generator.core.machine.MachineIdOverflowException;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLeaseConfig;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLeaseLostException;
import com.dyc.xiaohashu.id.generator.core.machine.MachineStatus;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcMachineIdAllocatorTest {

    private static final String NAMESPACE = "xiaohashu";
    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");

    @Test
    void acquireShouldInsertFirstMachineId() {
        FixedTimeService timeService = new FixedTimeService(NOW);
        JdbcMachineIdAllocator allocator = allocator(dataSource(), timeService);

        MachineLease lease = allocator.acquire(NAMESPACE, new InstanceIdentity("host-a", false), 3, 0);

        assertEquals(0, lease.machineId());
        assertEquals(MachineStatus.ACTIVE, lease.status());
        assertEquals(NOW.plusSeconds(30), lease.leaseExpiresAt());
    }

    @Test
    void acquireShouldReuseSameInstanceAndKeepLargestTimestamp() {
        FixedTimeService timeService = new FixedTimeService(NOW);
        JdbcMachineIdAllocator allocator = allocator(dataSource(), timeService);
        allocator.acquire(NAMESPACE, new InstanceIdentity("host-a", false), 3, 100);
        timeService.set(NOW.plusSeconds(1));

        MachineLease lease = allocator.acquire(NAMESPACE, new InstanceIdentity("host-a", false), 3, 50);

        assertEquals(0, lease.machineId());
        assertEquals(100, lease.lastTimestamp());
        assertEquals(1, lease.version());
    }

    @Test
    void acquireShouldReclaimExpiredUnstableMachineId() {
        FixedTimeService timeService = new FixedTimeService(NOW);
        JdbcMachineIdAllocator allocator = allocator(dataSource(), timeService);
        allocator.acquire(NAMESPACE, new InstanceIdentity("host-a", false), 3, 100);
        timeService.set(NOW.plusSeconds(31));

        MachineLease lease = allocator.acquire(NAMESPACE, new InstanceIdentity("host-b", false), 3, 120);

        assertEquals(0, lease.machineId());
        assertEquals("host-b", lease.instanceIdentity().instanceId());
        assertEquals(120, lease.lastTimestamp());
    }

    @Test
    void releaseShouldKeepStableMachineIdForSameInstance() {
        FixedTimeService timeService = new FixedTimeService(NOW);
        JdbcMachineIdAllocator allocator = allocator(dataSource(), timeService);
        MachineLease stableLease = allocator.acquire(NAMESPACE, new InstanceIdentity("stable-host", true), 1, 0);
        allocator.release(stableLease);

        MachineLease otherLease = allocator.acquire(NAMESPACE, new InstanceIdentity("host-b", false), 1, 0);
        MachineLease reacquiredStableLease = allocator.acquire(NAMESPACE, new InstanceIdentity("stable-host", true), 1, 0);

        assertEquals(1, otherLease.machineId());
        assertEquals(0, reacquiredStableLease.machineId());
    }

    @Test
    void heartbeatShouldRefreshLeaseAndRejectStaleVersion() {
        FixedTimeService timeService = new FixedTimeService(NOW);
        JdbcMachineIdAllocator allocator = allocator(dataSource(), timeService);
        MachineLease lease = allocator.acquire(NAMESPACE, new InstanceIdentity("host-a", false), 3, 0);
        timeService.set(NOW.plusSeconds(1));

        MachineLease refreshedLease = allocator.heartbeat(lease, 1234);

        assertEquals(1, refreshedLease.version());
        assertEquals(1234, refreshedLease.lastTimestamp());
        assertThrows(MachineLeaseLostException.class, () -> allocator.heartbeat(lease, 1235));
    }

    @Test
    void acquireShouldRejectWhenMachineIdRangeIsFull() {
        FixedTimeService timeService = new FixedTimeService(NOW);
        JdbcMachineIdAllocator allocator = allocator(dataSource(), timeService);
        allocator.acquire(NAMESPACE, new InstanceIdentity("host-a", false), 0, 0);

        assertThrows(MachineIdOverflowException.class, () -> allocator.acquire(NAMESPACE, new InstanceIdentity("host-b", false), 0, 0));
    }

    @Test
    void acquireShouldAssignUniqueMachineIdsConcurrently() throws InterruptedException {
        FixedTimeService timeService = new FixedTimeService(NOW);
        JdbcMachineIdAllocator allocator = new JdbcMachineIdAllocator(
                dataSource(),
                MachineLeaseConfig.defaults(),
                timeService,
                new JdbcRetryTemplate(new JdbcRetryConfig(10, Duration.ofMillis(1)))
        );
        int threadCount = 8;
        Set<Integer> machineIds = ConcurrentHashMap.newKeySet();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            String instanceId = "host-" + i;
            executor.execute(() -> {
                try {
                    startLatch.await();
                    MachineLease lease = allocator.acquire(NAMESPACE, new InstanceIdentity(instanceId, false), 15, 0);
                    machineIds.add(lease.machineId());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertEquals(threadCount, machineIds.size());
    }

    private static JdbcMachineIdAllocator allocator(DataSource dataSource, TimeService timeService) {
        return new JdbcMachineIdAllocator(dataSource, MachineLeaseConfig.defaults(), timeService, new JdbcRetryTemplate(JdbcRetryConfig.defaults()));
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        new JdbcSchemaInitializer(dataSource).initialize();
        return dataSource;
    }

    private static final class FixedTimeService implements TimeService {

        private final AtomicLong currentMillis;

        private FixedTimeService(Instant initialTime) {
            this.currentMillis = new AtomicLong(initialTime.toEpochMilli());
        }

        private void set(Instant instant) {
            currentMillis.set(instant.toEpochMilli());
        }

        @Override
        public Instant now() {
            return Instant.ofEpochMilli(currentMillis.get());
        }
    }
}
