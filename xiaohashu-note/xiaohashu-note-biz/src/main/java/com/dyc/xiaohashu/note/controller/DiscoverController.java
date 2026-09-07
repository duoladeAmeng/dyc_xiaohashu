package com.dyc.xiaohashu.note.controller;

import com.dyc.framework.biz.operationlog.aspect.ApiOperationLog;
import com.dyc.framework.common.response.PageResponse;
import com.dyc.xiaohashu.note.dto.req.FindDiscoverNotePageListReq;
import com.dyc.xiaohashu.note.dto.rsp.FindDiscoverNoteRsp;
import com.dyc.xiaohashu.note.service.DiscoverService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/discover")
@Slf4j
public class DiscoverController {

    @Resource
    private DiscoverService discoverService;

    @PostMapping(value = "/note/list")
    @ApiOperationLog(description = "发现页-查询笔记列表")
    public PageResponse<FindDiscoverNoteRsp> findNoteList(@Validated @RequestBody FindDiscoverNotePageListReq findDiscoverNoteListReq) {
        return discoverService.findNoteList(findDiscoverNoteListReq);
    }
}
