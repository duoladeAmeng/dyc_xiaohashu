package com.dyc.xiaohashu.user.id;

/**
 * 用户 ID 与小哈书号生成边界。
 */
public interface UserIdGenerator {

    Long nextUserId();

    String nextXiaohashuId();
}
