package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.InvalidIdGeneratorConfigurationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MachineLeaseConfigTest {

    @Test
    void defaultsShouldMatchLeaseModel() {
        MachineLeaseConfig config = MachineLeaseConfig.defaults();

        assertEquals(Duration.ofSeconds(10), config.heartbeatInterval());
        assertEquals(Duration.ofSeconds(30), config.leaseTimeout());
        assertEquals(2, config.maxHeartbeatFailures());
    }

    @Test
    void constructorShouldRejectLeaseTimeoutNotGreaterThanHeartbeatInterval() {
        assertThrows(InvalidIdGeneratorConfigurationException.class, () -> new MachineLeaseConfig(
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                0
        ));
    }
}
