package com.dyc.xiaohashu.auth.service;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.auth.dto.req.UpdatePasswordReq;
import com.dyc.xiaohashu.auth.dto.req.UserLoginReq;

public interface AuthService {

    /**
     * 登录与注册
     * @param userLoginReq
     * @return
     */
    Response<String> loginAndRegister(UserLoginReq userLoginReq);

    /**
     * 退出登录
     * @return
     */
    Response<?> logout();

    /**
     * 修改密码
     * @param updatePasswordReq
     * @return
     */
    Response<?> updatePassword(UpdatePasswordReq updatePasswordReq);
}
