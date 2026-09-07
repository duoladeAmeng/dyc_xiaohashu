package com.dyc.xiaohashu.note.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopNoteReq {

    @NotNull(message = "笔记 ID 不能为空")
    private Long id;

    @NotNull(message = "置顶状态不能为空")
    private Boolean isTop;

}
