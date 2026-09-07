package com.dyc.xiaohashu.user.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindUserProfileReq {

    /**
     * 用户 ID。为空时查询当前登录用户。
     */
    private Long userId;
}
