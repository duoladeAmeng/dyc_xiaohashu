package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for segment-chain prefetching.
 *
 * @param safeDistance minimum number of base segments kept ahead
 * @param maxPrefetchDistance maximum adaptive prefetch distance
 * @param prefetchPeriod period for background maintenance
 * @param prefetchRetryCount maximum retry count for one prefetch attempt
 * @param prefetchRetryBackoff initial retry backoff
 */
public record SegmentChainConfig(
        int safeDistance,
        int maxPrefetchDistance,
        Duration prefetchPeriod,
        int prefetchRetryCount,
        Duration prefetchRetryBackoff
) {

    public static final int DEFAULT_SAFE_DISTANCE = 2;
    public static final int DEFAULT_MAX_PREFETCH_DISTANCE = 1024;
    public static final Duration DEFAULT_PREFETCH_PERIOD = Duration.ofSeconds(1);
    public static final int DEFAULT_PREFETCH_RETRY_COUNT = 3;
    public static final Duration DEFAULT_PREFETCH_RETRY_BACKOFF = Duration.ofMillis(50);

    public SegmentChainConfig {
        Objects.requireNonNull(prefetchPeriod, "prefetchPeriod must not be null");
        Objects.requireNonNull(prefetchRetryBackoff, "prefetchRetryBackoff must not be null");
        if (safeDistance <= 0) {
            throw new InvalidIdGeneratorConfigurationException("safeDistance must be greater than zero");
        }
        if (maxPrefetchDistance < safeDistance) {
            throw new InvalidIdGeneratorConfigurationException("maxPrefetchDistance must not be less than safeDistance");
        }
        if (prefetchPeriod.isNegative() || prefetchPeriod.isZero()) {
            throw new InvalidIdGeneratorConfigurationException("prefetchPeriod must be greater than zero");
        }
        if (prefetchRetryCount < 0) {
            throw new InvalidIdGeneratorConfigurationException("prefetchRetryCount must not be negative");
        }
        if (prefetchRetryBackoff.isNegative() || prefetchRetryBackoff.isZero()) {
            throw new InvalidIdGeneratorConfigurationException("prefetchRetryBackoff must be greater than zero");
        }
    }

    /**
     * Returns default segment-chain configuration.
     *
     * @return default config
     */
    public static SegmentChainConfig defaults() {
        return new SegmentChainConfig(
                DEFAULT_SAFE_DISTANCE,
                DEFAULT_MAX_PREFETCH_DISTANCE,
                DEFAULT_PREFETCH_PERIOD,
                DEFAULT_PREFETCH_RETRY_COUNT,
                DEFAULT_PREFETCH_RETRY_BACKOFF
        );
    }
}
