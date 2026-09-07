package com.dyc.xiaohashu.id.generator.api;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.id.generator.constant.ApiConstants;
import com.dyc.xiaohashu.id.generator.dto.req.BatchGenerateIdReqDTO;
import com.dyc.xiaohashu.id.generator.dto.req.GenerateIdReqDTO;
import com.dyc.xiaohashu.id.generator.dto.resp.BatchGenerateIdRspDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface DistributedIdFeignApi {

    String PREFIX = "/id-generator";

    /**
     * 按类型获取一个 ID。
     *
     * @param generateIdReqDTO 请求参数
     * @return ID
     */
    @PostMapping(value = PREFIX + "/next")
    Response<Long> nextId(@RequestBody GenerateIdReqDTO generateIdReqDTO);

    /**
     * 按类型获取一个字符串 ID。
     *
     * @param generateIdReqDTO 请求参数
     * @return 字符串 ID
     */
    @PostMapping(value = PREFIX + "/next/string")
    Response<String> nextIdAsString(@RequestBody GenerateIdReqDTO generateIdReqDTO);

    /**
     * 按类型批量获取 ID。
     *
     * @param batchGenerateIdReqDTO 请求参数
     * @return ID 列表
     */
    @PostMapping(value = PREFIX + "/batch")
    Response<BatchGenerateIdRspDTO> batchGenerateIds(@RequestBody BatchGenerateIdReqDTO batchGenerateIdReqDTO);

    /**
     * 获取一个 Snowflake ID。
     *
     * @return ID
     */
    @GetMapping(value = PREFIX + "/snowflake")
    Response<Long> nextSnowflakeId();

    /**
     * 获取一个 Snowflake 字符串 ID。
     *
     * @return 字符串 ID
     */
    @GetMapping(value = PREFIX + "/snowflake/string")
    Response<String> nextSnowflakeIdAsString();

    /**
     * 获取一个数据库号段 ID。
     *
     * @return ID
     */
    @GetMapping(value = PREFIX + "/segment")
    Response<Long> nextSegmentId();

    /**
     * 获取一个数据库号段字符串 ID。
     *
     * @return 字符串 ID
     */
    @GetMapping(value = PREFIX + "/segment/string")
    Response<String> nextSegmentIdAsString();

    /**
     * 获取一个 SegmentChain ID。
     *
     * @return ID
     */
    @GetMapping(value = PREFIX + "/segment-chain")
    Response<Long> nextSegmentChainId();

    /**
     * 获取一个 SegmentChain 字符串 ID。
     *
     * @return 字符串 ID
     */
    @GetMapping(value = PREFIX + "/segment-chain/string")
    Response<String> nextSegmentChainIdAsString();
}
