package com.dyc.xiaohashu.id.generator.spring.boot.actuate;

import com.dyc.xiaohashu.id.generator.core.machine.MachineLease;
import com.dyc.xiaohashu.id.generator.core.segment.IdSegment;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.core.time.TimeService;
import com.dyc.xiaohashu.id.generator.spring.boot.lifecycle.SnowflakeLeaseLifecycle;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Health indicator for distributed ID generator safety state.
 */
public class DistributedIdHealthIndicator implements HealthIndicator {

    private final List<SnowflakeIdGenerator> snowflakeIdGenerators;
    private final List<SnowflakeLeaseLifecycle> snowflakeLeaseLifecycles;
    private final List<SegmentIdGenerator> segmentIdGenerators;
    private final List<SegmentChainIdGenerator> segmentChainIdGenerators;
    private final Optional<DataSource> dataSource;
    private final TimeService timeService;

    public DistributedIdHealthIndicator(
            List<SnowflakeIdGenerator> snowflakeIdGenerators,
            List<SnowflakeLeaseLifecycle> snowflakeLeaseLifecycles,
            List<SegmentIdGenerator> segmentIdGenerators,
            List<SegmentChainIdGenerator> segmentChainIdGenerators,
            Optional<DataSource> dataSource,
            TimeService timeService
    ) {
        this.snowflakeIdGenerators = List.copyOf(Objects.requireNonNull(snowflakeIdGenerators, "snowflakeIdGenerators must not be null"));
        this.snowflakeLeaseLifecycles = List.copyOf(Objects.requireNonNull(snowflakeLeaseLifecycles, "snowflakeLeaseLifecycles must not be null"));
        this.segmentIdGenerators = List.copyOf(Objects.requireNonNull(segmentIdGenerators, "segmentIdGenerators must not be null"));
        this.segmentChainIdGenerators = List.copyOf(Objects.requireNonNull(segmentChainIdGenerators, "segmentChainIdGenerators must not be null"));
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.timeService = Objects.requireNonNull(timeService, "timeService must not be null");
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();

        dataSource.ifPresent(value -> details.put("database", databaseHealth(value, problems)));
        details.put("snowflake", snowflakeHealth(problems));
        details.put("segment", segmentHealth());
        details.put("segmentChain", segmentChainHealth(problems));

        if (problems.isEmpty()) {
            return Health.up().withDetails(details).build();
        }
        details.put("problems", problems);
        return Health.status(Status.OUT_OF_SERVICE).withDetails(details).build();
    }

    private Map<String, Object> databaseHealth(DataSource source, List<String> problems) {
        Map<String, Object> detail = new LinkedHashMap<>();
        try (Connection connection = source.getConnection()) {
            boolean valid = connection.isValid(1);
            detail.put("valid", valid);
            if (!valid) {
                problems.add("database connection is not valid");
            }
        } catch (SQLException exception) {
            detail.put("valid", false);
            detail.put("error", exception.getMessage());
            problems.add("database connection failed");
        }
        return detail;
    }

    private List<Map<String, Object>> snowflakeHealth(List<String> problems) {
        Instant now = timeService.now();
        List<Map<String, Object>> details = new ArrayList<>();
        for (SnowflakeIdGenerator generator : snowflakeIdGenerators) {
            MachineLease lease = generator.machineLease();
            boolean canGenerate = lease.canGenerateAt(now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("namespace", lease.namespace());
            detail.put("machineId", lease.machineId());
            detail.put("status", lease.status().name());
            detail.put("canGenerate", canGenerate);
            detail.put("leaseExpiresAt", lease.leaseExpiresAt());
            detail.put("lastTimestamp", generator.lastTimestamp());
            detail.put("generatedTotal", generator.generatedTotal());
            detail.put("clockBackwardsTotal", generator.clockBackwardsTotal());
            detail.put("sequenceOverflowTotal", generator.sequenceOverflowTotal());
            details.add(detail);
            if (!canGenerate) {
                problems.add("snowflake lease cannot generate for namespace " + lease.namespace());
            }
        }
        for (SnowflakeLeaseLifecycle lifecycle : snowflakeLeaseLifecycles) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("running", lifecycle.isRunning());
            detail.put("consecutiveHeartbeatFailures", lifecycle.consecutiveHeartbeatFailures());
            detail.put("heartbeatSuccessTotal", lifecycle.heartbeatSuccessTotal());
            detail.put("heartbeatFailureTotal", lifecycle.heartbeatFailureTotal());
            detail.put("leaseLostTotal", lifecycle.leaseLostTotal());
            detail.put("releaseFailureTotal", lifecycle.releaseFailureTotal());
            detail.put("lastHeartbeatAt", lifecycle.lastHeartbeatAt());
            detail.put("lastHeartbeatFailureAt", lifecycle.lastHeartbeatFailureAt());
            detail.put("lastHeartbeatFailureMessage", lifecycle.lastHeartbeatFailureMessage());
            details.add(detail);
        }
        return details;
    }

    private List<Map<String, Object>> segmentHealth() {
        List<Map<String, Object>> details = new ArrayList<>();
        for (SegmentIdGenerator generator : segmentIdGenerators) {
            IdSegment segment = generator.currentSegment();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("type", generator instanceof SegmentChainIdGenerator ? "segment-chain" : "segment");
            detail.put("namespace", segment.namespace());
            detail.put("tag", segment.tag());
            detail.put("remaining", segment.remaining());
            detail.put("startInclusive", segment.startInclusive());
            detail.put("endInclusive", segment.endInclusive());
            detail.put("generatedTotal", generator.generatedTotal());
            detail.put("segmentAllocatedTotal", generator.segmentAllocatedTotal());
            detail.put("segmentFetchFailureTotal", generator.segmentFetchFailureTotal());
            details.add(detail);
        }
        return details;
    }

    private List<Map<String, Object>> segmentChainHealth(List<String> problems) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (SegmentChainIdGenerator generator : segmentChainIdGenerators) {
            IdSegment segment = generator.currentSegment();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("namespace", segment.namespace());
            detail.put("tag", segment.tag());
            detail.put("closed", generator.closed());
            detail.put("chainDistance", generator.chainDistance());
            detail.put("prefetchDistance", generator.prefetchDistance());
            detail.put("prefetchSuccessTotal", generator.prefetchSuccessTotal());
            detail.put("prefetchFailureTotal", generator.prefetchFailureTotal());
            details.add(detail);
            if (generator.closed()) {
                problems.add("segment-chain generator is closed for tag " + segment.tag());
            }
        }
        return details;
    }
}
