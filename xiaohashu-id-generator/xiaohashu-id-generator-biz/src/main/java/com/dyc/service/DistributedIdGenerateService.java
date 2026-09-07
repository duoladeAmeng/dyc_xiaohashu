package com.dyc.service;

import com.dyc.xiaohashu.id.generator.dto.resp.BatchGenerateIdRspDTO;
import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;

public interface DistributedIdGenerateService {

    /**
     * 获取一个 ID。
     *
     * @param type ID 生成器类型
     * @return ID
     */
    Long nextId(IdGeneratorType type);

    /**
     * 获取一个字符串 ID。
     *
     * @param type ID 生成器类型
     * @return 字符串 ID
     */
    String nextIdAsString(IdGeneratorType type);

    /**
     * 批量获取 ID。
     *
     * @param type ID 生成器类型
     * @param size 批量获取数量
     * @return ID 列表
     */
    BatchGenerateIdRspDTO batchGenerateIds(IdGeneratorType type, Integer size);
}
