package com.dyc.xiaohashu.note.dto.rsp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNoteDetailRsp {

    private Long id;
    private Integer type;
    private String title;
    private String content;
    private List<String> imgUris;

    private List<FindTopicRsp> topics;

    private Long creatorId;
    private String creatorName;
    private String avatar;
    private String videoUri;

    private String updateTime;

    private Integer visible;

    private String likeTotal;

    private String collectTotal;

    private String commentTotal;

}
