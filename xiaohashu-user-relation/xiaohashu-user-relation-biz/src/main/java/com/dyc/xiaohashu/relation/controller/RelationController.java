package com.dyc.xiaohashu.relation.controller;

import com.dyc.framework.biz.operationlog.aspect.ApiOperationLog;
import com.dyc.framework.common.response.PageResponse;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.relation.model.req.*;
import com.dyc.xiaohashu.relation.model.rsp.*;
import com.dyc.xiaohashu.relation.service.RelationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/relation")
@Slf4j
public class RelationController {

    @Resource
    private RelationService relationService;

    @PostMapping("/follow")
    @ApiOperationLog(description = "关注用户")
    public Response<?> follow(@Validated @RequestBody FollowUserReq followUserReq) {
        return relationService.follow(followUserReq);
    }

    @PostMapping("/unfollow")
    @ApiOperationLog(description = "取关用户")
    public Response<?> unfollow(@Validated @RequestBody UnfollowUserReq unfollowUserReq) {
        return relationService.unfollow(unfollowUserReq);
    }

    @PostMapping("/following/list")
    @ApiOperationLog(description = "查询用户关注列表")
    public PageResponse<FindFollowingUserRsp> findFollowingList(@Validated @RequestBody FindFollowingListReq findFollowingListReq) {
        return relationService.findFollowingList(findFollowingListReq);
    }

    @PostMapping("/fans/list")
    @ApiOperationLog(description = "查询用户粉丝列表")
    public PageResponse<FindFansUserRsp> findFansList(@Validated @RequestBody FindFansListReq findFansListReq) {
        return relationService.findFansList(findFansListReq);
    }

}
