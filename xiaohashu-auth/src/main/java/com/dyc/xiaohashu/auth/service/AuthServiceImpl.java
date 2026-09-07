package com.dyc.xiaohashu.auth.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.dyc.framework.biz.context.holder.LoginUserContextHolder;
import com.dyc.framework.common.exception.BizException;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.auth.dto.req.UpdatePasswordReq;
import com.dyc.xiaohashu.auth.dto.req.UserLoginReq;
import com.dyc.xiaohashu.auth.enums.LoginTypeEnum;
import com.dyc.xiaohashu.auth.enums.ResponseCodeEnum;
import com.dyc.xiaohashu.auth.rpc.UserRpcService;
import com.dyc.xiaohashu.user.dto.resp.FindUserByPhoneRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private VerificationCodeService verificationCodeService;

    @Override
    public Response<String> loginAndRegister(UserLoginReq userLoginReq) {
        String phone = userLoginReq.getPhone();
        Integer type = userLoginReq.getType();

        LoginTypeEnum loginTypeEnum = LoginTypeEnum.valueOf(type);
        if (Objects.isNull(loginTypeEnum)) {
            throw new BizException(ResponseCodeEnum.LOGIN_TYPE_ERROR);
        }

        Long userId = null;

        switch (loginTypeEnum) {
            case VERIFICATION_CODE:
                String verificationCode = userLoginReq.getCode();
                if (verificationCode == null || verificationCode.trim().isEmpty()) {
                    throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }

                verificationCodeService.verifyCode(phone, verificationCode);

                Long userIdTmp = userRpcService.registerUser(phone);
                if (Objects.isNull(userIdTmp)) {
                    throw new BizException(ResponseCodeEnum.LOGIN_FAIL);
                }

                userId = userIdTmp;
                break;
            case PASSWORD:
                String password = userLoginReq.getPassword();
                FindUserByPhoneRspDTO findUserByPhoneRspDTO = userRpcService.findUserByPhone(phone);
                if (Objects.isNull(findUserByPhoneRspDTO)) {
                    throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
                }

                String encodePassword = findUserByPhoneRspDTO.getPassword();
                boolean isPasswordCorrect = passwordEncoder.matches(password, encodePassword);
                if (!isPasswordCorrect) {
                    throw new BizException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
                }

                userId = findUserByPhoneRspDTO.getId();
                break;
            default:
                break;
        }

        StpUtil.login(userId);
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return Response.success(tokenInfo.tokenValue);
    }

    @Override
    public Response<?> logout() {
        Long userId = LoginUserContextHolder.getUserId();

        // 退出登录 (指定用户 ID)
        StpUtil.logout(userId);
        return Response.success();
    }

    @Override
    public Response<?> updatePassword(UpdatePasswordReq updatePasswordReq) {
        String newPassword = updatePasswordReq.getNewPassword();
        String encodePassword = passwordEncoder.encode(newPassword);
        userRpcService.updatePassword(encodePassword);
        return Response.success();
    }
}
