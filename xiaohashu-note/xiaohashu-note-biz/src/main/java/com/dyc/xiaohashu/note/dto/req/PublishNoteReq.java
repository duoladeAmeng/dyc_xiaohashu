package com.dyc.xiaohashu.note.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PublishNoteReq {

    @NotNull(message = "笔记类型不能为空")
    private Integer type;

    private List<String> imgUris;

    private String videoUri;

    @NotBlank(message = "笔记标题不能为空")
    private String title;

    private String content;


    /**
     * 支持用户添加多话题
     */
    private List<Object> topics;

    /**
     * 目前平台不支持人工智能对话题归类到不同频道下，故牺牲一点用户体验，让用户手动选择频道
     */
    @NotNull(message = "频道不能为空")
    private Long channelId;

}
