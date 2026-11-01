package com.dyc.xiaohashu.relation.model.rsp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindFollowingUserRsp {

    private Long userId;

    private String avatar;

    private String nickname;

    private String introduction;

}
