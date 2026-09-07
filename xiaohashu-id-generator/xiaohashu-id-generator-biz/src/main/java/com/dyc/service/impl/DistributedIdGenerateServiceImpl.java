package com.dyc.service.impl;

import com.dyc.service.DistributedIdGenerateService;
import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;
import com.dyc.xiaohashu.id.generator.core.IdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentChainIdGenerator;
import com.dyc.xiaohashu.id.generator.core.segment.SegmentIdGenerator;
import com.dyc.xiaohashu.id.generator.core.snowflake.SnowflakeIdGenerator;
import com.dyc.xiaohashu.id.generator.dto.resp.BatchGenerateIdRspDTO;
import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DistributedIdGenerateServiceImpl implements DistributedIdGenerateService {

    private static final int MAX_BATCH_SIZE = 1000;

    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final SegmentIdGenerator segmentIdGenerator;
    private final SegmentChainIdGenerator segmentChainIdGenerator;

    @Autowired
    public DistributedIdGenerateServiceImpl(
            ObjectProvider<SnowflakeIdGenerator> snowflakeIdGeneratorProvider,
            @Qualifier("segmentIdGenerator") ObjectProvider<SegmentIdGenerator> segmentIdGeneratorProvider,
            ObjectProvider<SegmentChainIdGenerator> segmentChainIdGeneratorProvider
    ) {
        this(
                snowflakeIdGeneratorProvider.getIfAvailable(),
                segmentIdGeneratorProvider.getIfAvailable(),
                segmentChainIdGeneratorProvider.getIfAvailable()
        );
    }

    DistributedIdGenerateServiceImpl(
            SnowflakeIdGenerator snowflakeIdGenerator,
            SegmentIdGenerator segmentIdGenerator,
            SegmentChainIdGenerator segmentChainIdGenerator
    ) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.segmentIdGenerator = segmentIdGenerator;
        this.segmentChainIdGenerator = segmentChainIdGenerator;
    }

    @Override
    public Long nextId(IdGeneratorType type) {
        return selectGenerator(type).nextId();
    }

    @Override
    public String nextIdAsString(IdGeneratorType type) {
        return selectGenerator(type).nextIdAsString();
    }

    @Override
    public BatchGenerateIdRspDTO batchGenerateIds(IdGeneratorType type, Integer size) {
        validateBatchSize(size);
        IdGenerator idGenerator = selectGenerator(type);
        List<Long> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(idGenerator.nextId());
        }
        return BatchGenerateIdRspDTO.builder()
                .type(type)
                .size(ids.size())
                .ids(ids)
                .build();
    }

    private IdGenerator selectGenerator(IdGeneratorType type) {
        Objects.requireNonNull(type, "ID 生成器类型不能为空");
        return switch (type) {
            case SNOWFLAKE -> requireAvailable(snowflakeIdGenerator, type);
            case SEGMENT -> requireAvailable(segmentIdGenerator, type);
            case SEGMENT_CHAIN -> requireAvailable(segmentChainIdGenerator, type);
        };
    }

    private IdGenerator requireAvailable(IdGenerator idGenerator, IdGeneratorType type) {
        if (idGenerator == null) {
            throw new GeneratorUnavailableException(type + " generator is not available");
        }
        return idGenerator;
    }

    private void validateBatchSize(Integer size) {
        Objects.requireNonNull(size, "批量获取数量不能为空");
        if (size < 1 || size > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("批量获取数量必须大于等于 1, 小于等于 " + MAX_BATCH_SIZE);
        }
    }
}
