package com.dyc.xiaohashu.note.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.note.domain.dataobject.TopicDO;
import com.dyc.xiaohashu.note.domain.mapper.TopicDOMapper;
import com.dyc.xiaohashu.note.dto.req.FindTopicListReq;
import com.dyc.xiaohashu.note.dto.rsp.FindTopicRsp;
import com.dyc.xiaohashu.note.service.TopicService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
public class TopicServiceImpl implements TopicService {

    @Resource
    private TopicDOMapper topicDOMapper;

    @Override
    public Response<List<FindTopicRsp>> findTopicList(FindTopicListReq findTopicListReq) {
        String keyword = findTopicListReq.getKeyword();

        List<TopicDO> topicDOS = topicDOMapper.selectByLikeName(keyword);

        List<FindTopicRsp> findTopicRspVOS = null;
        if (CollUtil.isNotEmpty(topicDOS)) {
            findTopicRspVOS = topicDOS.stream()
                    .map(topicDO -> FindTopicRsp.builder()
                            .id(topicDO.getId())
                            .name(topicDO.getName())
                            .build())
                    .toList();
        }

        return Response.success(findTopicRspVOS);
    }
}
