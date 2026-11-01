package com.dyc.xiaohashu.search.service;

import com.dyc.framework.common.response.PageResponse;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.search.dto.req.RebuildUserDocumentReqDTO;
import com.dyc.xiaohashu.search.model.req.SearchUserReq;
import com.dyc.xiaohashu.search.model.rsp.SearchUserRsp;

public interface UserService {

    /**
     * 搜索用户
     * @param searchUserReq
     * @return
     */
    PageResponse<SearchUserRsp> searchUser(SearchUserReq searchUserReq);

    /**
     * 重建用户文档
     * @param rebuildUserDocumentReqDTO
     * @return
     */
    Response<Long> rebuildDocument(RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO);
}
