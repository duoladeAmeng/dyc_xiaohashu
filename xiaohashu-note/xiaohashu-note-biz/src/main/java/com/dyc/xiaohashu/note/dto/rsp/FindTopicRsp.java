package com.dyc.xiaohashu.note.dto.rsp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindTopicRsp {

    /**
     * 话题 ID
     */
    private Long id;

    /**
     * 话题名称
     */
    private String name;

}
