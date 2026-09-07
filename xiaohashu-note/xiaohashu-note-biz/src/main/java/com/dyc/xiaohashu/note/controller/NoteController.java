package com.dyc.xiaohashu.note.controller;

import com.dyc.framework.biz.operationlog.aspect.ApiOperationLog;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.note.dto.req.DeleteNoteReq;
import com.dyc.xiaohashu.note.dto.req.FindNoteDetailReq;
import com.dyc.xiaohashu.note.dto.req.PublishNoteReq;
import com.dyc.xiaohashu.note.dto.req.TopNoteReq;
import com.dyc.xiaohashu.note.dto.req.UpdateNoteReq;
import com.dyc.xiaohashu.note.dto.req.UpdateNoteVisibleOnlyMeReq;
import com.dyc.xiaohashu.note.dto.rsp.FindNoteDetailRsp;
import com.dyc.xiaohashu.note.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/note")
@Slf4j
public class NoteController {

    @Resource
    private NoteService noteService;

    @PostMapping(value = "/publish")
    @ApiOperationLog(description = "笔记发布")
    public Response<?> publishNote(@Validated @RequestBody PublishNoteReq publishNoteReq) {
        return noteService.publishNote(publishNoteReq);
    }

    @PostMapping(value = "/detail")
    @ApiOperationLog(description = "笔记详情")
    public Response<FindNoteDetailRsp> findNoteDetail(@Validated @RequestBody FindNoteDetailReq findNoteDetailReq) {
        return noteService.findNoteDetail(findNoteDetailReq);
    }

    @PostMapping(value = "/update")
    @ApiOperationLog(description = "笔记修改")
    public Response<?> updateNote(@Validated @RequestBody UpdateNoteReq updateNoteReq) {
        return noteService.updateNote(updateNoteReq);
    }

    @PostMapping(value = "/delete")
    @ApiOperationLog(description = "删除笔记")
    public Response<?> deleteNote(@Validated @RequestBody DeleteNoteReq deleteNoteReq) {
        return noteService.deleteNote(deleteNoteReq);
    }

    @PostMapping(value = "/visible/onlyme")
    @ApiOperationLog(description = "笔记仅对自己可见")
    public Response<?> visibleOnlyMe(@Validated @RequestBody UpdateNoteVisibleOnlyMeReq updateNoteVisibleOnlyMeReq) {
        return noteService.visibleOnlyMe(updateNoteVisibleOnlyMeReq);
    }

    @PostMapping(value = "/top")
    @ApiOperationLog(description = "置顶/取消置顶笔记")
    public Response<?> topNote(@Validated @RequestBody TopNoteReq topNoteReq) {
        return noteService.topNote(topNoteReq);
    }
}
