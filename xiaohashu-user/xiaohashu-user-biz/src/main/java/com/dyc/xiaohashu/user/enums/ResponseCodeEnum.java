package com.dyc.xiaohashu.user.enums;

import com.dyc.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    SYSTEM_ERROR("USER-10000", "系统错误"),
    PARAM_NOT_VALID("USER-10001", "参数错误"),

    NICK_NAME_VALID_FAIL("USER-20001", "昵称请设置 2-24 个字符，不能使用特殊字符"),
    XIAOHASHU_ID_VALID_FAIL("USER-20002", "小哈书号请设置 6-15 个字符，仅可使用英文、数字、下划线"),
    SEX_VALID_FAIL("USER-20003", "性别错误"),
    INTRODUCTION_VALID_FAIL("USER-20004", "个人简介请设置 1-100 个字符"),
    UPLOAD_AVATAR_FAIL("USER-20005", "头像上传失败"),
    UPLOAD_BACKGROUND_IMG_FAIL("USER-20006", "背景图上传失败"),
    USER_NOT_FOUND("USER-20007", "该用户不存在"),
    CURRENT_USER_NOT_FOUND("USER-20008", "当前登录用户不存在");

    private final String errorCode;
    private final String errorMessage;
}
