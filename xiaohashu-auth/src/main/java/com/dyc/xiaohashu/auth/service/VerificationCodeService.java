package com.dyc.xiaohashu.auth.service;

import com.dyc.framework.common.exception.BizException;

/**
 * 验证码服务接口
 */
public interface VerificationCodeService {

    /**
     * 发送验证码
     *
     * @param phone 手机号
     */
    void sendCode(String phone);

    /**
     * 校验验证码
     *
     * @param phone 手机号
     * @param code  验证码
     * @throws BizException 验证码过期或错误时抛出异常
     */
    void verifyCode(String phone, String code);
}
