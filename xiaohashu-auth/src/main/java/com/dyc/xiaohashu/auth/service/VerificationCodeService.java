package com.dyc.xiaohashu.auth.service;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.auth.dto.req.SendCodeReq;

public interface VerificationCodeService {

    /**
     * 发送短信验证码
     *
     * @param sendCodeReq
     * @return
     */
    Response<?> send(SendCodeReq sendCodeReq);
}
