package com.dyc.xiaohashu.user.controller;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.user.dto.req.FindUserByIdReqDTO;
import com.dyc.xiaohashu.user.dto.req.FindUserByPhoneReqDTO;
import com.dyc.xiaohashu.user.dto.req.FindUsersByIdsReqDTO;
import com.dyc.xiaohashu.user.dto.req.RegisterUserReqDTO;
import com.dyc.xiaohashu.user.dto.req.UpdateUserPasswordReqDTO;
import com.dyc.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import com.dyc.xiaohashu.user.dto.resp.FindUserByPhoneRspDTO;
import com.dyc.xiaohashu.user.model.vo.FindUserProfileReq;
import com.dyc.xiaohashu.user.model.vo.FindUserProfileRsp;
import com.dyc.xiaohashu.user.model.vo.UpdateUserInfoReq;
import com.dyc.xiaohashu.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> updateUserInfo(@Validated UpdateUserInfoReq updateUserInfoReqVO) {
        return userService.updateUserInfo(updateUserInfoReqVO);
    }

    @PostMapping("/profile")
    public Response<FindUserProfileRsp> findUserProfile(@Validated @RequestBody FindUserProfileReq findUserProfileReqVO) {
        return userService.findUserProfile(findUserProfileReqVO);
    }

    @PostMapping("/register")
    public Response<Long> register(@Validated @RequestBody RegisterUserReqDTO registerUserReqDTO) {
        return userService.register(registerUserReqDTO);
    }

    @PostMapping("/findByPhone")
    public Response<FindUserByPhoneRspDTO> findByPhone(@Validated @RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        return userService.findByPhone(findUserByPhoneReqDTO);
    }

    @PostMapping("/password/update")
    public Response<?> updatePassword(@Validated @RequestBody UpdateUserPasswordReqDTO updateUserPasswordReqDTO) {
        return userService.updatePassword(updateUserPasswordReqDTO);
    }

    @PostMapping("/findById")
    public Response<FindUserByIdRspDTO> findById(@Validated @RequestBody FindUserByIdReqDTO findUserByIdReqDTO) {
        return userService.findById(findUserByIdReqDTO);
    }

    @PostMapping("/findByIds")
    public Response<List<FindUserByIdRspDTO>> findByIds(@Validated @RequestBody FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        return userService.findByIds(findUsersByIdsReqDTO);
    }
}
