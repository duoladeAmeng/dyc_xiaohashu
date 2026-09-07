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
public class FindDiscoverNotePageListReq {

    /**
     * 频道 ID
     */
    private Long channelId;

    @NotNull(message = "页码不能为空")
    private Integer pageNo = 1;
}
