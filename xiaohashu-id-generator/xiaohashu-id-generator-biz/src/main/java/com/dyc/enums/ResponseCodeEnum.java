package com.dyc.enums;

import com.dyc.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    SYSTEM_ERROR("ID-GENERATOR-10000", "系统错误"),
    PARAM_NOT_VALID("ID-GENERATOR-10001", "参数错误"),
    GENERATOR_UNAVAILABLE("ID-GENERATOR-20001", "ID 生成器不可用"),
    GENERATE_ID_FAIL("ID-GENERATOR-20002", "ID 生成失败");

    private final String errorCode;
    private final String errorMessage;
}
