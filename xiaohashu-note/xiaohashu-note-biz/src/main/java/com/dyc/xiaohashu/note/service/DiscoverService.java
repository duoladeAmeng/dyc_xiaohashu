package com.dyc.xiaohashu.note.service;

import com.dyc.framework.common.response.PageResponse;
import com.dyc.xiaohashu.note.dto.req.FindDiscoverNotePageListReq;
import com.dyc.xiaohashu.note.dto.rsp.FindDiscoverNoteRsp;

public interface DiscoverService {

    PageResponse<FindDiscoverNoteRsp> findNoteList(FindDiscoverNotePageListReq findDiscoverNoteListReq);
}
