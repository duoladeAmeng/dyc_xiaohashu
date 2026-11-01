package com.dyc.xiaohashu.count.service;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.count.dto.rsp.FindNoteCountsByIdRspDTO;
import com.dyc.xiaohashu.count.dto.req.FindNoteCountsByIdsReqDTO;

import java.util.List;

public interface NoteCountService {

    /**
     * 批量查询笔记计数
     * @param findNoteCountsByIdsReqDTO
     * @return
     */
    Response<List<FindNoteCountsByIdRspDTO>> findNotesCountData(FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO);
}
