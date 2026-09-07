package com.dyc.xiaohashu.note.service;




import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.note.dto.req.FindTopicListReq;
import com.dyc.xiaohashu.note.dto.rsp.FindTopicRsp;

import java.util.List;


public interface TopicService {

    Response<List<FindTopicRsp>> findTopicList(FindTopicListReq findTopicListReq);
}
