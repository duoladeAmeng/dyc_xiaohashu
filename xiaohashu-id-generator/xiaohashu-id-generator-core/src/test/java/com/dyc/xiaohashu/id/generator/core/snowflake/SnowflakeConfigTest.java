package com.dyc.xiaohashu.id.generator.core.snowflake;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnowflakeConfigTest {

    @Test
    void defaultsShouldMatchProductionLayout() {
        SnowflakeConfig config = SnowflakeConfig.defaults();

        assertEquals(41, config.timestampBits());
        assertEquals(10, config.machineBits());
        assertEquals(12, config.sequenceBits());
        assertEquals(1023, config.maxMachineId());
        assertEquals(4095, config.maxSequence());
        assertEquals(2047, config.sequenceResetThreshold());
    }

    @Test
    void constructorShouldRejectUnsafeBitLayout() {
        assertThrows(InvalidIdGeneratorConfigurationException.class, () -> new SnowflakeConfig(
                Instant.EPOCH,
                41,
                10,
                13,
                0,
                ClockBackwardsPolicy.DEFAULT
        ));
    }

    @Test
    void validateUsableAtShouldRejectFutureEpoch() {
        SnowflakeConfig config = SnowflakeConfig.defaults();

        assertThrows(InvalidIdGeneratorConfigurationException.class, () -> config.validateUsableAt(config.epoch()));
    }
}
