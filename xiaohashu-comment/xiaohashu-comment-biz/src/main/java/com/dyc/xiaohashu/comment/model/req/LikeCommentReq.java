package com.dyc.xiaohashu.comment.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LikeCommentReq {

    @NotNull(message = "评论 ID 不能为空")
    private Long commentId;

}
