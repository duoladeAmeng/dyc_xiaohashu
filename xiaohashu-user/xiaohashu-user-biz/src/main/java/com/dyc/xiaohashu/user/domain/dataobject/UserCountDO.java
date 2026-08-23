package com.dyc.xiaohashu.user.domain.dataobject;

public class UserCountDO {
    private Long id;

    private Long userId;

    private Long fansTotal;

    private Long followingTotal;

    private Long noteTotal;

    private Long likeTotal;

    private Long collectTotal;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFansTotal() {
        return fansTotal;
    }

    public void setFansTotal(Long fansTotal) {
        this.fansTotal = fansTotal;
    }

    public Long getFollowingTotal() {
        return followingTotal;
    }

    public void setFollowingTotal(Long followingTotal) {
        this.followingTotal = followingTotal;
    }

    public Long getNoteTotal() {
        return noteTotal;
    }

    public void setNoteTotal(Long noteTotal) {
        this.noteTotal = noteTotal;
    }

    public Long getLikeTotal() {
        return likeTotal;
    }

    public void setLikeTotal(Long likeTotal) {
        this.likeTotal = likeTotal;
    }

    public Long getCollectTotal() {
        return collectTotal;
    }

    public void setCollectTotal(Long collectTotal) {
        this.collectTotal = collectTotal;
    }
}