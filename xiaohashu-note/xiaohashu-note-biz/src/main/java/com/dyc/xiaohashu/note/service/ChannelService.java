package com.dyc.xiaohashu.note.service;


import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.note.dto.rsp.FindChannelRsp;

import java.util.List;

public interface ChannelService {

    /**
     * 查询所有频道
     * @return
     */
    Response<List<FindChannelRsp>> findChannelList();
}
