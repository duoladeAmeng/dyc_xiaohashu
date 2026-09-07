package com.dyc.controller;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.id.generator.api.DistributedIdFeignApi;
import com.dyc.service.DistributedIdGenerateService;
import com.dyc.xiaohashu.id.generator.dto.req.BatchGenerateIdReqDTO;
import com.dyc.xiaohashu.id.generator.dto.req.GenerateIdReqDTO;
import com.dyc.xiaohashu.id.generator.dto.resp.BatchGenerateIdRspDTO;
import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(DistributedIdFeignApi.PREFIX)
public class DistributedIdController {

    @Resource
    private DistributedIdGenerateService distributedIdGenerateService;

    @PostMapping("/next")
    public Response<Long> nextId(@Validated @RequestBody GenerateIdReqDTO generateIdReqDTO) {
        return Response.success(distributedIdGenerateService.nextId(generateIdReqDTO.getType()));
    }

    @PostMapping("/next/string")
    public Response<String> nextIdAsString(@Validated @RequestBody GenerateIdReqDTO generateIdReqDTO) {
        return Response.success(distributedIdGenerateService.nextIdAsString(generateIdReqDTO.getType()));
    }

    @PostMapping("/batch")
    public Response<BatchGenerateIdRspDTO> batchGenerateIds(@Validated @RequestBody BatchGenerateIdReqDTO batchGenerateIdReqDTO) {
        return Response.success(distributedIdGenerateService.batchGenerateIds(
                batchGenerateIdReqDTO.getType(),
                batchGenerateIdReqDTO.getSize()
        ));
    }

    @GetMapping("/snowflake")
    public Response<Long> nextSnowflakeId() {
        return Response.success(distributedIdGenerateService.nextId(IdGeneratorType.SNOWFLAKE));
    }

    @GetMapping("/snowflake/string")
    public Response<String> nextSnowflakeIdAsString() {
        return Response.success(distributedIdGenerateService.nextIdAsString(IdGeneratorType.SNOWFLAKE));
    }

    @GetMapping("/segment")
    public Response<Long> nextSegmentId() {
        return Response.success(distributedIdGenerateService.nextId(IdGeneratorType.SEGMENT));
    }

    @GetMapping("/segment/string")
    public Response<String> nextSegmentIdAsString() {
        return Response.success(distributedIdGenerateService.nextIdAsString(IdGeneratorType.SEGMENT));
    }

    @GetMapping("/segment-chain")
    public Response<Long> nextSegmentChainId() {
        return Response.success(distributedIdGenerateService.nextId(IdGeneratorType.SEGMENT_CHAIN));
    }

    @GetMapping("/segment-chain/string")
    public Response<String> nextSegmentChainIdAsString() {
        return Response.success(distributedIdGenerateService.nextIdAsString(IdGeneratorType.SEGMENT_CHAIN));
    }
}
