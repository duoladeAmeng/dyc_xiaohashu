package com.dyc.xiaohashu.count.api;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.count.constant.ApiConstants;
import com.dyc.xiaohashu.count.dto.rsp.FindNoteCountsByIdRspDTO;
import com.dyc.xiaohashu.count.dto.req.FindNoteCountsByIdsReqDTO;
import com.dyc.xiaohashu.count.dto.req.FindUserCountsByIdReqDTO;
import com.dyc.xiaohashu.count.dto.rsp.FindUserCountsByIdRspDTO;
import com.dyc.xiaohashu.count.fallback.CountFeignApiFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = ApiConstants.SERVICE_NAME, fallback = CountFeignApiFallback.class)
//@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface CountFeignApi {

    String PREFIX = "/count";

    /**
     * 查询用户计数
     *
     * @param findUserCountsByIdReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/user/data")
    Response<FindUserCountsByIdRspDTO> findUserCount(@RequestBody FindUserCountsByIdReqDTO findUserCountsByIdReqDTO);

    /**
     * 批量查询笔记计数
     *
     * @param findNoteCountsByIdsReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/notes/data")
    Response<List<FindNoteCountsByIdRspDTO>> findNotesCount(@RequestBody FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO);

}
