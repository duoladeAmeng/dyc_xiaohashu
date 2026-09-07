package com.dyc.xiaohashu.id.generator.biz.service.impl;

import com.dyc.service.impl.DistributedIdGenerateServiceImpl;
import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.dto.resp.BatchGenerateIdRspDTO;
import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DistributedIdGenerateServiceImplTest {

    @Test
    void nextIdShouldRouteByGeneratorType() {
        SnowflakeIdGenerator snowflakeIdGenerator = mock(SnowflakeIdGenerator.class);
        SegmentIdGenerator segmentIdGenerator = mock(SegmentIdGenerator.class);
        SegmentChainIdGenerator segmentChainIdGenerator = mock(SegmentChainIdGenerator.class);
        when(snowflakeIdGenerator.nextId()).thenReturn(11L);
        when(segmentIdGenerator.nextId()).thenReturn(22L);
        when(segmentChainIdGenerator.nextId()).thenReturn(33L);
        DistributedIdGenerateServiceImpl service = new DistributedIdGenerateServiceImpl(
                snowflakeIdGenerator,
                segmentIdGenerator,
                segmentChainIdGenerator
        );

        assertEquals(11L, service.nextId(IdGeneratorType.SNOWFLAKE));
        assertEquals(22L, service.nextId(IdGeneratorType.SEGMENT));
        assertEquals(33L, service.nextId(IdGeneratorType.SEGMENT_CHAIN));
    }

    @Test
    void batchGenerateIdsShouldReturnRequestedSize() {
        SegmentChainIdGenerator segmentChainIdGenerator = mock(SegmentChainIdGenerator.class);
        when(segmentChainIdGenerator.nextId()).thenReturn(101L, 102L, 103L);
        DistributedIdGenerateServiceImpl service = new DistributedIdGenerateServiceImpl(null, null, segmentChainIdGenerator);

        BatchGenerateIdRspDTO response = service.batchGenerateIds(IdGeneratorType.SEGMENT_CHAIN, 3);

        assertEquals(IdGeneratorType.SEGMENT_CHAIN, response.getType());
        assertEquals(3, response.getSize());
        assertEquals(3, response.getIds().size());
        assertEquals(101L, response.getIds().get(0));
        assertEquals(103L, response.getIds().get(2));
    }

    @Test
    void nextIdShouldRejectUnavailableGenerator() {
        DistributedIdGenerateServiceImpl service = new DistributedIdGenerateServiceImpl(
                (SnowflakeIdGenerator) null,
                null,
                null
        );

        assertThrows(GeneratorUnavailableException.class, () -> service.nextId(IdGeneratorType.SNOWFLAKE));
    }

    @Test
    void batchGenerateIdsShouldRejectInvalidSize() {
        SegmentIdGenerator segmentIdGenerator = mock(SegmentIdGenerator.class);
        DistributedIdGenerateServiceImpl service = new DistributedIdGenerateServiceImpl(null, segmentIdGenerator, null);

        assertThrows(IllegalArgumentException.class, () -> service.batchGenerateIds(IdGeneratorType.SEGMENT, 0));
        assertThrows(IllegalArgumentException.class, () -> service.batchGenerateIds(IdGeneratorType.SEGMENT, 1001));
    }
}
