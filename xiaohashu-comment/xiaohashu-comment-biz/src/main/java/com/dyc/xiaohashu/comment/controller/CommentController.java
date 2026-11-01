package com.dyc.xiaohashu.comment.controller;

import com.dyc.framework.biz.operationlog.aspect.ApiOperationLog;
import com.dyc.framework.common.response.PageResponse;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.comment.model.req.*;
import com.dyc.xiaohashu.comment.model.rsp.*;
import com.dyc.xiaohashu.comment.service.CommentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comment")
@Slf4j
public class CommentController {

    @Resource
    private CommentService commentService;

    @PostMapping("/publish")
    @ApiOperationLog(description = "发布评论")
    public Response<?> publishComment(@Validated @RequestBody PublishCommentReq publishCommentReq) {
        return commentService.publishComment(publishCommentReq);
    }

    @PostMapping("/list")
    @ApiOperationLog(description = "评论分页查询")
    public PageResponse<FindCommentItemRsp> findCommentPageList(@Validated @RequestBody FindCommentPageListReq findCommentPageListReq) {
        return commentService.findCommentPageList(findCommentPageListReq);
    }

    @PostMapping("/child/list")
    @ApiOperationLog(description = "二级评论分页查询")
    public PageResponse<FindChildCommentItemRsp> findChildCommentPageList(@Validated @RequestBody FindChildCommentPageListReq findChildCommentPageListReq) {
        return commentService.findChildCommentPageList(findChildCommentPageListReq);
    }

    @PostMapping("/like")
    @ApiOperationLog(description = "评论点赞")
    public Response<?> likeComment(@Validated @RequestBody LikeCommentReq likeCommentReq) {
        return commentService.likeComment(likeCommentReq);
    }

    @PostMapping("/unlike")
    @ApiOperationLog(description = "评论取消点赞")
    public Response<?> unlikeComment(@Validated @RequestBody UnLikeCommentReq unLikeCommentReq) {
        return commentService.unlikeComment(unLikeCommentReq);
    }

    @PostMapping("/delete")
    @ApiOperationLog(description = "删除评论")
    public Response<?> deleteComment(@Validated @RequestBody DeleteCommentReq deleteCommentReq) {
        return commentService.deleteComment(deleteCommentReq);
    }
}
