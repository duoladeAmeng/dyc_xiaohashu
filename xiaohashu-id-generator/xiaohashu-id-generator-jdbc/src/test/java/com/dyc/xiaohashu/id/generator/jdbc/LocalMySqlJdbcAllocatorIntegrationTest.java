package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.machine.InstanceIdentity;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.machine.MachineLeaseConfig;
import com.dyc.xiaohashu.id.generator.core.segment.IdSegment;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMySqlJdbcAllocatorIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    private final String machineNamespace = "local-machine-" + UUID.randomUUID();
    private final String segmentNamespace = "local-segment-" + UUID.randomUUID();
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("distributed.id.local-mysql.enabled"));
        dataSource = dataSource();
        executeSchema(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (dataSource == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement deleteMachines = connection.prepareStatement("delete from id_machine where namespace = ?");
             PreparedStatement deleteMachineSequence = connection.prepareStatement("delete from id_machine_sequence where namespace = ?");
             PreparedStatement deleteSegments = connection.prepareStatement("delete from id_segment where namespace = ?")) {
            deleteMachines.setString(1, machineNamespace);
            deleteMachines.executeUpdate();
            deleteMachineSequence.setString(1, machineNamespace);
            deleteMachineSequence.executeUpdate();
            deleteSegments.setString(1, segmentNamespace);
            deleteSegments.executeUpdate();
        }
    }

    @Test
    void schemaShouldCreateExpectedTablesAgainstLocalMySql() throws SQLException {
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
    void machineAllocatorShouldAssignUniqueMachineIdsForOneHundredInstancesAgainstLocalMySql() throws InterruptedException {
        JdbcMachineIdAllocator allocator = new JdbcMachineIdAllocator(
                dataSource,
                MachineLeaseConfig.defaults(),
                new FixedTimeService(NOW),
                new JdbcRetryTemplate(new JdbcRetryConfig(150, Duration.ZERO))
        );
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
                    MachineLease lease = allocator.acquire(machineNamespace, new InstanceIdentity(instanceId, false), 127, 0);
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
        failOnFirstError(errors);
        assertEquals(instanceCount, machineIds.size());
    }

    @Test
    void segmentAllocatorShouldAssignNonOverlappingSegmentsForOneHundredCallsAgainstLocalMySql() throws InterruptedException {
        JdbcSegmentAllocator allocator = new JdbcSegmentAllocator(
                dataSource,
                new FixedTimeService(NOW),
                new JdbcRetryTemplate(new JdbcRetryConfig(50, Duration.ZERO))
        );
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
                    IdSegment segment = allocator.nextSegment(segmentNamespace, "note", 10);
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
        failOnFirstError(errors);
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
        dataSource.setUrl(System.getProperty(
                "distributed.id.local-mysql.url",
                "jdbc:mysql://127.0.0.1:3306/xiaohashu?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
        ));
        dataSource.setUser(System.getProperty("distributed.id.local-mysql.username", "root"));
        dataSource.setPassword(System.getProperty("distributed.id.local-mysql.password", "1234"));
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

    private static void failOnFirstError(Queue<Throwable> errors) {
        Throwable error = errors.peek();
        if (error != null) {
            AssertionError assertionError = new AssertionError("concurrent task failed", error);
            errors.forEach(assertionError::addSuppressed);
            throw assertionError;
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
