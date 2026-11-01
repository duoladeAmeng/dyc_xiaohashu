package com.dyc.xiaohashu.count.service;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.count.dto.req.FindUserCountsByIdReqDTO;
import com.dyc.xiaohashu.count.dto.rsp.FindUserCountsByIdRspDTO;

public interface UserCountService {

    /**
     * 查询用户相关计数
     * @param findUserCountsByIdReqDTO
     * @return
     */
    Response<FindUserCountsByIdRspDTO> findUserCountData(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO);
}
