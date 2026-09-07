package com.dyc.service;

import com.dyc.xiaohashu.id.generator.dto.resp.BatchGenerateIdRspDTO;
import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;

public interface DistributedIdGenerateService {

    long nextId(IdGeneratorType type);

    BatchGenerateIdRspDTO batchGenerateIds(IdGeneratorType type, int size);
}
