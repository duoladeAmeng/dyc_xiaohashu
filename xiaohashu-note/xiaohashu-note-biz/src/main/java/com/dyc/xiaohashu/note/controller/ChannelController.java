package com.dyc.xiaohashu.note.controller;


import com.dyc.framework.biz.operationlog.aspect.ApiOperationLog;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.note.dto.rsp.FindChannelRsp;
import com.dyc.xiaohashu.note.service.ChannelService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author: 犬小哈
 * @date: 2024/4/4 13:22
 * @version: v1.0.0
 * @description: 频道
 **/
@RestController
@RequestMapping("/channel")
@Slf4j
public class ChannelController {

    @Resource
    private ChannelService channelService;

    @PostMapping(value = "/list")
    @ApiOperationLog(description = "获取所有频道")
    public Response<List<FindChannelRsp>> findChannelList() {
        return channelService.findChannelList();
    }

}
