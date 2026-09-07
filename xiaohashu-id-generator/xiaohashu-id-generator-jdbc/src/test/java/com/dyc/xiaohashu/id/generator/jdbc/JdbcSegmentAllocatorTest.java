package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.segment.IdSegment;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentOverflowException;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

class JdbcSegmentAllocatorTest {

    private static final String NAMESPACE = "xiaohashu";
    private static final String TAG = "note";
    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");

    @Test
    void nextSegmentShouldInsertFirstSegment() {
        JdbcSegmentAllocator allocator = allocator(dataSource());

        IdSegment segment = allocator.nextSegment(NAMESPACE, TAG, 5);

        assertEquals(1, segment.startInclusive());
        assertEquals(5, segment.endInclusive());
        assertEquals(5, segment.size());
    }

    @Test
    void nextSegmentShouldAllocateContinuousNonOverlappingSegments() {
        JdbcSegmentAllocator allocator = allocator(dataSource());

        IdSegment first = allocator.nextSegment(NAMESPACE, TAG, 3);
        IdSegment second = allocator.nextSegment(NAMESPACE, TAG, 4);

        assertEquals(1, first.startInclusive());
        assertEquals(3, first.endInclusive());
        assertEquals(4, second.startInclusive());
        assertEquals(7, second.endInclusive());
    }

    @Test
    void nextSegmentShouldIsolateDifferentTags() {
        JdbcSegmentAllocator allocator = allocator(dataSource());

        IdSegment noteSegment = allocator.nextSegment(NAMESPACE, "note", 3);
        IdSegment userSegment = allocator.nextSegment(NAMESPACE, "user", 3);

        assertEquals(1, noteSegment.startInclusive());
        assertEquals(1, userSegment.startInclusive());
    }

    @Test
    void nextSegmentShouldAllocateNonOverlappingSegmentsConcurrently() throws InterruptedException {
        JdbcSegmentAllocator allocator = new JdbcSegmentAllocator(
                dataSource(),
                new FixedTimeService(NOW),
                new JdbcRetryTemplate(new JdbcRetryConfig(10, Duration.ofMillis(1)))
        );
        int threadCount = 8;
        Set<String> ranges = ConcurrentHashMap.newKeySet();
        List<IdSegment> segments = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    startLatch.await();
                    IdSegment segment = allocator.nextSegment(NAMESPACE, TAG, 10);
                    synchronized (segments) {
                        segments.add(segment);
                    }
                    ranges.add(segment.startInclusive() + ":" + segment.endInclusive());
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
        segments.sort(Comparator.comparingLong(IdSegment::startInclusive));
        assertEquals(threadCount, ranges.size());
        for (int i = 0; i < segments.size(); i++) {
            IdSegment segment = segments.get(i);
            assertEquals(i * 10L + 1, segment.startInclusive());
            assertEquals((i + 1L) * 10L, segment.endInclusive());
        }
    }

    @Test
    void nextSegmentShouldRejectMaxIdOverflow() throws SQLException {
        DataSource dataSource = dataSource();
        insertSegment(dataSource, Long.MAX_VALUE - 1);
        JdbcSegmentAllocator allocator = allocator(dataSource);

        assertThrows(SegmentOverflowException.class, () -> allocator.nextSegment(NAMESPACE, TAG, 2));
    }

    private static JdbcSegmentAllocator allocator(DataSource dataSource) {
        return new JdbcSegmentAllocator(dataSource, new FixedTimeService(NOW), new JdbcRetryTemplate(JdbcRetryConfig.defaults()));
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        new JdbcSchemaInitializer(dataSource).initialize();
        return dataSource;
    }

    private static void insertSegment(DataSource dataSource, long maxId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into id_segment (namespace, tag, max_id, step, version, last_fetch_at, updated_at, created_at)
                     values (?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            Timestamp now = Timestamp.from(NOW);
            statement.setString(1, NAMESPACE);
            statement.setString(2, TAG);
            statement.setLong(3, maxId);
            statement.setLong(4, 1L);
            statement.setLong(5, 0L);
            statement.setTimestamp(6, now);
            statement.setTimestamp(7, now);
            statement.setTimestamp(8, now);
            statement.executeUpdate();
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
