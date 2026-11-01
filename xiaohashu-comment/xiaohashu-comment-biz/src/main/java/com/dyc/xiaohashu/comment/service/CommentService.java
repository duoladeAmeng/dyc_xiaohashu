package com.dyc.xiaohashu.comment.service;

import com.dyc.framework.common.response.PageResponse;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.comment.model.req.*;
import com.dyc.xiaohashu.comment.model.rsp.*;

public interface CommentService {

    /**
     * 发布评论
     * @param publishCommentReq
     * @return
     */
    Response<?> publishComment(PublishCommentReq publishCommentReq);

    /**
     * 评论列表分页查询
     * @param findCommentPageListReq
     * @return
     */
    PageResponse<FindCommentItemRsp> findCommentPageList(FindCommentPageListReq findCommentPageListReq);

    /**
     * 二级评论分页查询
     * @param findChildCommentPageListReq
     * @return
     */
    PageResponse<FindChildCommentItemRsp> findChildCommentPageList(FindChildCommentPageListReq findChildCommentPageListReq);

    /**
     * 评论点赞
     * @param likeCommentReq
     * @return
     */
    Response<?> likeComment(LikeCommentReq likeCommentReq);

    /**
     * 取消评论点赞
     * @param unLikeCommentReq
     * @return
     */
    Response<?> unlikeComment(UnLikeCommentReq unLikeCommentReq);

    /**
     * 删除评论
     * @param deleteCommentReq
     * @return
     */
    Response<?> deleteComment(DeleteCommentReq deleteCommentReq);

    /**
     * 删除本地评论缓存
     * @param commentId
     */
    void deleteCommentLocalCache(Long commentId);
}
