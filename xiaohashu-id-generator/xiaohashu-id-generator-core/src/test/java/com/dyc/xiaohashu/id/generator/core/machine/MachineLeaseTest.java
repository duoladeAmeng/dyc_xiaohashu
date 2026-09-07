package com.dyc.xiaohashu.id.generator.core.machine;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineLeaseTest {

    @Test
    void canGenerateAtShouldRequireActiveUnexpiredLease() {
        Instant now = Instant.parse("2026-09-06T00:00:00Z");
        InstanceIdentity instance = new InstanceIdentity("host:8080", false);
        MachineLease lease = new MachineLease("xiaohashu", 1, instance, MachineStatus.ACTIVE, 0, now, now.plusSeconds(30), 1);

        assertTrue(lease.canGenerateAt(now));
        assertFalse(lease.canGenerateAt(now.plusSeconds(30)));
        assertFalse(new MachineLease("xiaohashu", 1, instance, MachineStatus.RELEASED, 0, now, now.plusSeconds(30), 1).canGenerateAt(now));
    }
}
