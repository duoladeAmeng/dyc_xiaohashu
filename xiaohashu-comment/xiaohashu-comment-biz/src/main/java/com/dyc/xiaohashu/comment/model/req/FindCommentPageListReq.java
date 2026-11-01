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
public class FindCommentPageListReq {

    @NotNull(message = "笔记 ID 不能为空")
    private Long noteId;

    @NotNull(message = "页码不能为空")
    private Integer pageNo = 1;
}
