package com.dyc.xiaohashu.relation.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UnfollowUserReq {

    @NotNull(message = "被取关用户 ID 不能为空")
    private Long unfollowUserId;
}
