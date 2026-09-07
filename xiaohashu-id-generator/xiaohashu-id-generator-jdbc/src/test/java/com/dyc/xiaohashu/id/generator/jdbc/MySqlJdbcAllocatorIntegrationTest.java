package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.machine.InstanceIdentity;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLeaseConfig;
import com.dyc.xiaohashu.id.generator.core.segment.IdSegment;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class MySqlJdbcAllocatorIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("xiaohashu")
            .withUsername("root")
            .withPassword("1234");

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        dataSource = dataSource();
        executeSchema(dataSource);
        clearTables(dataSource);
    }

    @Test
    void schemaShouldCreateExpectedTablesAgainstMySql() throws SQLException {
        Set<String> tableNames = ConcurrentHashMap.newKeySet();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select table_name
                     from information_schema.tables
                     where table_schema = database()
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("table_name"));
            }
        }

        assertTrue(tableNames.contains("id_machine"));
        assertTrue(tableNames.contains("id_segment"));
    }

    @Test
    void machineAllocatorShouldAssignUniqueMachineIdsForOneHundredInstancesAgainstMySql() throws InterruptedException {
        JdbcMachineIdAllocator allocator = new JdbcMachineIdAllocator(
                dataSource,
                MachineLeaseConfig.defaults(),
                new FixedTimeService(NOW),
                new JdbcRetryTemplate(new JdbcRetryConfig(150, Duration.ZERO))
        );
        String namespace = "machine-" + UUID.randomUUID();
        int instanceCount = 100;
        Set<Integer> machineIds = ConcurrentHashMap.newKeySet();
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(instanceCount);
        ExecutorService executor = Executors.newFixedThreadPool(16);
        for (int i = 0; i < instanceCount; i++) {
            String instanceId = "host-" + i;
            executor.execute(() -> {
                try {
                    startLatch.await();
                    MachineLease lease = allocator.acquire(namespace, new InstanceIdentity(instanceId, false), 127, 0);
                    machineIds.add(lease.machineId());
                } catch (Throwable throwable) {
                    errors.add(throwable);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertTrue(errors.isEmpty(), () -> errors.peek().toString());
        assertEquals(instanceCount, machineIds.size());
    }

    @Test
    void segmentAllocatorShouldAssignNonOverlappingSegmentsForOneHundredCallsAgainstMySql() throws InterruptedException {
        JdbcSegmentAllocator allocator = new JdbcSegmentAllocator(
                dataSource,
                new FixedTimeService(NOW),
                new JdbcRetryTemplate(new JdbcRetryConfig(50, Duration.ZERO))
        );
        String namespace = "segment-" + UUID.randomUUID();
        int segmentCount = 100;
        List<IdSegment> segments = new ArrayList<>();
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(segmentCount);
        ExecutorService executor = Executors.newFixedThreadPool(16);
        for (int i = 0; i < segmentCount; i++) {
            executor.execute(() -> {
                try {
                    startLatch.await();
                    IdSegment segment = allocator.nextSegment(namespace, "note", 10);
                    synchronized (segments) {
                        segments.add(segment);
                    }
                } catch (Throwable throwable) {
                    errors.add(throwable);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertTrue(errors.isEmpty(), () -> errors.peek().toString());
        segments.sort(Comparator.comparingLong(IdSegment::startInclusive));
        assertEquals(segmentCount, segments.size());
        for (int i = 0; i < segments.size(); i++) {
            IdSegment segment = segments.get(i);
            assertEquals(i * 10L + 1, segment.startInclusive());
            assertEquals((i + 1L) * 10L, segment.endInclusive());
        }
    }

    private static DataSource dataSource() throws SQLException {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
    }

    private static void executeSchema(DataSource dataSource) throws SQLException, IOException {
        String schema = Files.readString(findSchemaPath());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : schema.split(";")) {
                String trimmedSql = sql.trim();
                if (!trimmedSql.isEmpty()) {
                    statement.execute(trimmedSql);
                }
            }
        }
    }

    private static Path findSchemaPath() {
        Path current = Path.of("").toAbsolutePath();
        for (Path path = current; path != null; path = path.getParent()) {
            Path candidate = path.resolve("schema").resolve("mysql.sql");
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path nestedCandidate = path.resolve("xiaohashu-id-generator").resolve("schema").resolve("mysql.sql");
            if (Files.exists(nestedCandidate)) {
                return nestedCandidate;
            }
        }
        throw new IllegalStateException("schema/mysql.sql not found");
    }

    private static void clearTables(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("delete from id_machine");
            statement.executeUpdate("delete from id_machine_sequence");
            statement.executeUpdate("delete from id_segment");
        }
    }

    private static final class FixedTimeService implements TimeService {

        private final AtomicLong currentMillis;

        private FixedTimeService(Instant initialTime) {
            this.currentMillis = new AtomicLong(initialTime.toEpochMilli());
        }

        @Override
        public Instant now() {
            return Instant.ofEpochMilli(currentMillis.get());
        }
    }
}
