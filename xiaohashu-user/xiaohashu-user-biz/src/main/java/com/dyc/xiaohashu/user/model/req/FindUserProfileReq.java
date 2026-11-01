package com.dyc.xiaohashu.user.model.req;

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
     * 用户 ID
     */
    private Long userId;

}
