package com.dyc.xiaohashu.id.generator.core.segment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdSegmentTest {

    @Test
    void tryNextIdShouldReturnIdsInsideClosedInterval() {
        IdSegment segment = new IdSegment("xiaohashu", "note", 1, 3);

        assertEquals(3, segment.remaining());
        assertEquals(1, segment.tryNextId());
        assertEquals(2, segment.tryNextId());
        assertTrue(segment.isAvailable());
        assertEquals(3, segment.tryNextId());
        assertFalse(segment.isAvailable());
        assertEquals(IdSegment.SEQUENCE_OVERFLOW, segment.tryNextId());
    }
}
