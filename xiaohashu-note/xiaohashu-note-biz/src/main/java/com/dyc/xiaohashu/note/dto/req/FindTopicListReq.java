package com.dyc.xiaohashu.note.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindTopicListReq {

    @NotBlank(message = "话题关键词不能为空")
    private String keyword;

}
