package com.dyc.xiaohashu.note.controller;

import com.dyc.framework.biz.operationlog.aspect.ApiOperationLog;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.note.dto.req.*;
import com.dyc.xiaohashu.note.dto.rsp.*;
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

    @PostMapping(value = "/like")
    @ApiOperationLog(description = "点赞笔记")
    public Response<?> likeNote(@Validated @RequestBody LikeNoteReq likeNoteReq) {
        return noteService.likeNote(likeNoteReq);
    }

    @PostMapping(value = "/unlike")
    @ApiOperationLog(description = "取消点赞笔记")
    public Response<?> unlikeNote(@Validated @RequestBody UnlikeNoteReq unlikeNoteReq) {
        return noteService.unlikeNote(unlikeNoteReq);
    }

    @PostMapping(value = "/collect")
    @ApiOperationLog(description = "收藏笔记")
    public Response<?> collectNote(@Validated @RequestBody CollectNoteReq collectNoteReq) {
        return noteService.collectNote(collectNoteReq);
    }

    @PostMapping(value = "/uncollect")
    @ApiOperationLog(description = "取消收藏笔记")
    public Response<?> unCollectNote(@Validated @RequestBody UnCollectNoteReq unCollectNoteReq) {
        return noteService.unCollectNote(unCollectNoteReq);
    }

    @PostMapping(value = "/isLikedAndCollectedData")
    @ApiOperationLog(description = "获取当前用户是否点赞、收藏数据")
    public Response<FindNoteIsLikedAndCollectedRsp> isLikedAndCollectedData(@Validated @RequestBody FindNoteIsLikedAndCollectedReq findNoteIsLikedAndCollectedReq) {
        return noteService.isLikedAndCollectedData(findNoteIsLikedAndCollectedReq);
    }

    @PostMapping(value = "/published/list")
    @ApiOperationLog(description = "用户主页 - 已发布笔记列表")
    public Response<FindPublishedNoteListRsp> findPublishedNoteList(@Validated @RequestBody FindPublishedNoteListReq findPublishedNoteListReq) {
        return noteService.findPublishedNoteList(findPublishedNoteListReq);
    }
}
