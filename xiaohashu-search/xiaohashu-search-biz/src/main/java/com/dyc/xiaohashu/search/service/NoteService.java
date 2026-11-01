package com.dyc.xiaohashu.search.service;

import com.dyc.framework.common.response.PageResponse;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.search.dto.req.RebuildNoteDocumentReqDTO;
import com.dyc.xiaohashu.search.model.req.SearchNoteReq;
import com.dyc.xiaohashu.search.model.rsp.SearchNoteRsp;

public interface NoteService {

    /**
     * 搜索笔记
     * @param searchNoteReq
     * @return
     */
    PageResponse<SearchNoteRsp> searchNote(SearchNoteReq searchNoteReq);

    /**
     * 重建笔记文档
     * @param rebuildNoteDocumentReqDTO
     * @return
     */
    Response<Long> rebuildDocument(RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO);
}
