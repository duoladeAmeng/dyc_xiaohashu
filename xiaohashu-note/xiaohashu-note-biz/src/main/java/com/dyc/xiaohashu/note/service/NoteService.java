package com.dyc.xiaohashu.note.service;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.note.dto.req.*;
import com.dyc.xiaohashu.note.dto.rsp.*;

public interface NoteService {

    /**
     * 笔记发布
     * @param publishNoteReq
     * @return
     */
    Response<?> publishNote(PublishNoteReq publishNoteReq);

    /**
     * 笔记详情
     * @param findNoteDetailReq
     * @return
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

    /**
     * 点赞笔记
     * @param likeNoteReq
     * @return
     */
    Response<?> likeNote(LikeNoteReq likeNoteReq);

    /**
     * 取消点赞笔记
     * @param unlikeNoteReq
     * @return
     */
    Response<?> unlikeNote(UnlikeNoteReq unlikeNoteReq);

    /**
     * 收藏笔记
     * @param collectNoteReq
     * @return
     */
    Response<?> collectNote(CollectNoteReq collectNoteReq);

    /**
     * 取消收藏笔记
     * @param unCollectNoteReq
     * @return
     */
    Response<?> unCollectNote(UnCollectNoteReq unCollectNoteReq);

    /**
     * 获取是否点赞、收藏数据
     * @param findNoteIsLikedAndCollectedReq
     * @return
     */
    Response<FindNoteIsLikedAndCollectedRsp> isLikedAndCollectedData(FindNoteIsLikedAndCollectedReq findNoteIsLikedAndCollectedReq);

    /**
     * 用户主页 - 查询已发布的笔记列表
     * @param findPublishedNoteListReq
     * @return
     */
    Response<FindPublishedNoteListRsp> findPublishedNoteList(FindPublishedNoteListReq findPublishedNoteListReq);

}
