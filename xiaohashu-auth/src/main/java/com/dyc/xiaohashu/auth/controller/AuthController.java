package com.dyc.xiaohashu.auth.controller;

import com.dyc.framework.biz.operationlog.aspect.ApiOperationLog;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.auth.dto.req.UpdatePasswordReq;
import com.dyc.xiaohashu.auth.dto.req.UserLoginReq;
import com.dyc.xiaohashu.auth.service.AuthService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Slf4j
public class AuthController {

    @Resource
    private AuthService authService;

    @PostMapping("/login")
    @ApiOperationLog(description = "用户登录/注册")
    public Response<String> loginAndRegister(@Validated @RequestBody UserLoginReq userLoginReq) {
        return authService.loginAndRegister(userLoginReq);
    }

    @PostMapping("/logout")
    @ApiOperationLog(description = "账号登出")
    public Response<?> logout() {
        return authService.logout();
    }

    @PostMapping("/password/update")
    @ApiOperationLog(description = "修改密码")
    public Response<?> updatePassword(@Validated @RequestBody UpdatePasswordReq updatePasswordReq) {
        return authService.updatePassword(updatePasswordReq);
    }

}
