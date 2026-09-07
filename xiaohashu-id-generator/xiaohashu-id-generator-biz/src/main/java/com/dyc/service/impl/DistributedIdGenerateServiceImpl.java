package com.dyc.service.impl;

import com.dyc.service.DistributedIdGenerateService;
import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;
import com.dyc.xiaohashu.id.generator.core.IdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.dto.resp.BatchGenerateIdRspDTO;
import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DistributedIdGenerateServiceImpl implements DistributedIdGenerateService {

    private static final int MAX_BATCH_SIZE = 1000;

    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final SegmentIdGenerator segmentIdGenerator;
    private final SegmentChainIdGenerator segmentChainIdGenerator;

    public DistributedIdGenerateServiceImpl(SnowflakeIdGenerator snowflakeIdGenerator,
                                            SegmentIdGenerator segmentIdGenerator,
                                            SegmentChainIdGenerator segmentChainIdGenerator) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.segmentIdGenerator = segmentIdGenerator;
        this.segmentChainIdGenerator = segmentChainIdGenerator;
    }

    @Override
    public long nextId(IdGeneratorType type) {
        return generator(type).nextId();
    }

    @Override
    public BatchGenerateIdRspDTO batchGenerateIds(IdGeneratorType type, int size) {
        if (size <= 0 || size > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batch size must be between 1 and " + MAX_BATCH_SIZE + ".");
        }
        IdGenerator generator = generator(type);
        List<Long> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(generator.nextId());
        }
        return BatchGenerateIdRspDTO.builder()
                .type(type)
                .size(size)
                .ids(ids)
                .build();
    }

    private IdGenerator generator(IdGeneratorType type) {
        if (type == null) {
            throw new IllegalArgumentException("type can not be null.");
        }
        IdGenerator generator = switch (type) {
            case SNOWFLAKE -> snowflakeIdGenerator;
            case SEGMENT -> segmentIdGenerator;
            case SEGMENT_CHAIN -> segmentChainIdGenerator;
        };
        if (generator == null) {
            throw new GeneratorUnavailableException("Id generator " + type + " is unavailable.");
        }
        return generator;
    }
}
