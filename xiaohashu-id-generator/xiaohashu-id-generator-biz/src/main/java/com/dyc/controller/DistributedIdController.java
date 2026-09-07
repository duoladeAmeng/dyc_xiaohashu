package com.dyc.controller;

import com.dyc.framework.common.response.Response;
import com.dyc.service.DistributedIdGenerateService;
import com.dyc.xiaohashu.id.generator.dto.req.BatchGenerateIdReqDTO;
import com.dyc.xiaohashu.id.generator.dto.req.GenerateIdReqDTO;
import com.dyc.xiaohashu.id.generator.dto.resp.BatchGenerateIdRspDTO;
import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/id-generator")
public class DistributedIdController {

    @Resource
    private DistributedIdGenerateService distributedIdGenerateService;

    @GetMapping("/snowflake")
    public Response<Long> nextSnowflakeId() {
        return Response.success(distributedIdGenerateService.nextId(IdGeneratorType.SNOWFLAKE));
    }

    @PostMapping("/next")
    public Response<Long> nextId(@Valid @RequestBody GenerateIdReqDTO request) {
        return Response.success(distributedIdGenerateService.nextId(request.getType()));
    }

    @PostMapping("/batch")
    public Response<BatchGenerateIdRspDTO> batchGenerateIds(@Valid @RequestBody BatchGenerateIdReqDTO request) {
        return Response.success(distributedIdGenerateService.batchGenerateIds(request.getType(), request.getSize()));
    }
}
