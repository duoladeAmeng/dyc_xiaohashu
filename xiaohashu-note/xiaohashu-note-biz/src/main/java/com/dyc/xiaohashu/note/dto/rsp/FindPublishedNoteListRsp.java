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
public class FindPublishedNoteListRsp {

    /**
     * 笔记分页数据
     */
    private List<NoteItemRsp> notes;

    /**
     * 下一页的游标
     */
    private Long nextCursor;

}
