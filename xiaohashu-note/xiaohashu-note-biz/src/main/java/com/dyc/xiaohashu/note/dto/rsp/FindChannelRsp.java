package com.dyc.xiaohashu.note.dto.rsp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindChannelRsp {

    /**
     * 频道 ID
     */
    private Long id;

    /**
     * 频道名称
     */
    private String name;

}
