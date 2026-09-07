package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SegmentChainConfigTest {

    @Test
    void defaultsShouldBeConservative() {
        SegmentChainConfig config = SegmentChainConfig.defaults();

        assertEquals(2, config.safeDistance());
        assertEquals(1024, config.maxPrefetchDistance());
        assertEquals(Duration.ofSeconds(1), config.prefetchPeriod());
        assertEquals(3, config.prefetchRetryCount());
    }

    @Test
    void constructorShouldRejectMaxPrefetchDistanceBelowSafeDistance() {
        assertThrows(InvalidIdGeneratorConfigurationException.class, () -> new SegmentChainConfig(
                4,
                3,
                Duration.ofSeconds(1),
                3,
                Duration.ofMillis(50)
        ));
    }
}
