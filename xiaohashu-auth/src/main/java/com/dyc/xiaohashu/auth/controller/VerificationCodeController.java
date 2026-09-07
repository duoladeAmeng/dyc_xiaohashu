package com.dyc.xiaohashu.auth.controller;

import com.dyc.framework.biz.operationlog.aspect.ApiOperationLog;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.auth.dto.req.SendCodeReq;
import com.dyc.xiaohashu.auth.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/verification")
@RequiredArgsConstructor
public class VerificationCodeController {

    private final VerificationCodeService verificationCodeService;

    @ApiOperationLog(description = "发送验证码")
    @PostMapping("/code/send")
    public Response<Void> sendCode(@RequestBody @Validated SendCodeReq request) {
        verificationCodeService.sendCode(request.getPhone());
        return Response.success();
    }
}
