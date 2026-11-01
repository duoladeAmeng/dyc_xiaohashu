package com.dyc.xiaohashu.relation.service;

import com.dyc.framework.common.response.PageResponse;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.relation.model.req.*;
import com.dyc.xiaohashu.relation.model.rsp.*;


public interface RelationService {

    /**
     * 关注用户
     * @param followUserReq
     * @return
     */
    Response<?> follow(FollowUserReq followUserReq);

    /**
     * 取关用户
     * @param unfollowUserReq
     * @return
     */
    Response<?> unfollow(UnfollowUserReq unfollowUserReq);

    /**
     * 查询关注列表
     * @param findFollowingListReq
     * @return
     */
    PageResponse<FindFollowingUserRsp> findFollowingList(FindFollowingListReq findFollowingListReq);

    /**
     * 查询关注列表
     * @param findFansListReq
     * @return
     */
    PageResponse<FindFansUserRsp> findFansList(FindFansListReq findFansListReq);
}
