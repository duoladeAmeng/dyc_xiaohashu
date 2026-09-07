package com.dyc.xiaohashu.note.service;


import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.note.dto.req.DeleteNoteReq;
import com.dyc.xiaohashu.note.dto.req.FindNoteDetailReq;
import com.dyc.xiaohashu.note.dto.req.PublishNoteReq;
import com.dyc.xiaohashu.note.dto.req.TopNoteReq;
import com.dyc.xiaohashu.note.dto.req.UpdateNoteReq;
import com.dyc.xiaohashu.note.dto.req.UpdateNoteVisibleOnlyMeReq;
import com.dyc.xiaohashu.note.dto.rsp.FindNoteDetailRsp;


public interface NoteService {

    /**
     * 笔记发布
     * @param publishNoteReq
     * @return
     */
    Response<?> publishNote(PublishNoteReq publishNoteReq);

    /**
     * 笔记详情
     */
    Response<FindNoteDetailRsp> findNoteDetail(FindNoteDetailReq findNoteDetailReq);


    /**
     * 笔记更新
     * @param updateNoteReq
     * @return
     */
    Response<?> updateNote(UpdateNoteReq updateNoteReq);

    /**
     * 删除本地笔记缓存
     * @param noteId
     */
    void deleteNoteLocalCache(Long noteId);

    /**
     * 删除笔记
     * @param deleteNoteReq
     * @return
     */
    Response<?> deleteNote(DeleteNoteReq deleteNoteReq);

    /**
     * 笔记仅对自己可见
     * @param updateNoteVisibleOnlyMeReq
     * @return
     */
    Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReq updateNoteVisibleOnlyMeReq);

    /**
     * 笔记置顶 / 取消置顶
     * @param topNoteReq
     * @return
     */
    Response<?> topNote(TopNoteReq topNoteReq);
//
//    /**
//     * 点赞笔记
//     * @param likeNoteReqVO
//     * @return
//     */
//    Response<?> likeNote(LikeNoteReqVO likeNoteReqVO);
//
//    /**
//     * 取消点赞笔记
//     * @param unlikeNoteReqVO
//     * @return
//     */
//    Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO);
//
//    /**
//     * 收藏笔记
//     * @param collectNoteReqVO
//     * @return
//     */
//    Response<?> collectNote(CollectNoteReqVO collectNoteReqVO);
//
//    /**
//     * 取消收藏笔记
//     * @param unCollectNoteReqVO
//     * @return
//     */
//    Response<?> unCollectNote(UnCollectNoteReqVO unCollectNoteReqVO);
//
//    /**
//     * 获取是否点赞、收藏数据
//     * @param findNoteIsLikedAndCollectedReqVO
//     * @return
//     */
//    Response<FindNoteIsLikedAndCollectedRspVO> isLikedAndCollectedData(FindNoteIsLikedAndCollectedReqVO findNoteIsLikedAndCollectedReqVO);

}
