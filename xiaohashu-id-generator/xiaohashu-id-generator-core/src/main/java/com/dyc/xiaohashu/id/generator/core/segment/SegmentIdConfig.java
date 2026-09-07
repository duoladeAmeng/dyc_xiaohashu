package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;

/**
 * Configuration for plain segment ID generation.
 *
 * @param defaultStep default segment size
 */
public record SegmentIdConfig(long defaultStep) {

    public static final long DEFAULT_STEP = 10_000L;

    public SegmentIdConfig {
        if (defaultStep <= 0) {
            throw new InvalidIdGeneratorConfigurationException("defaultStep must be greater than zero");
        }
    }

    /**
     * Returns default segment configuration.
     *
     * @return default config
     */
    public static SegmentIdConfig defaults() {
        return new SegmentIdConfig(DEFAULT_STEP);
    }
}
