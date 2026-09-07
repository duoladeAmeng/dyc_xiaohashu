package com.dyc.xiaohashu.user.service;

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

import java.util.List;

public interface UserService {

    Response<?> updateUserInfo(UpdateUserInfoReq updateUserInfoReqVO);

    Response<Long> register(RegisterUserReqDTO registerUserReqDTO);

    Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO);

    Response<List<FindUserByIdRspDTO>> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO);

    Response<FindUserProfileRsp> findUserProfile(FindUserProfileReq findUserProfileReqVO);
}
