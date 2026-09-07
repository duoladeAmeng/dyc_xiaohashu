package com.dyc.xiaohashu.note.controller;

import com.dyc.framework.biz.operationlog.aspect.ApiOperationLog;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.note.dto.req.FindTopicListReq;
import com.dyc.xiaohashu.note.dto.rsp.FindTopicRsp;
import com.dyc.xiaohashu.note.service.TopicService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/topic")
@Slf4j
public class TopicController {

    @Resource
    private TopicService topicService;

    @PostMapping(value = "/list")
    @ApiOperationLog(description = "模糊查询话题列表")
    public Response<List<FindTopicRsp>> findTopicList(@Validated @RequestBody FindTopicListReq findTopicListReq) {
        return topicService.findTopicList(findTopicListReq);
    }

}
