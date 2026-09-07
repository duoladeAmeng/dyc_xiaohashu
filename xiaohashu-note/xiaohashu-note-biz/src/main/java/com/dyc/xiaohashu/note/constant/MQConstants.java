package com.dyc.xiaohashu.note.constant;

public interface MQConstants {

    /**
     * Topic 主题：删除笔记本地缓存
     */
    String TOPIC_DELETE_NOTE_LOCAL_CACHE = "DeleteNoteLocalCacheTopic";
    /**
     * Topic: 笔记操作（发布、删除）
     */
    String TOPIC_NOTE_OPERATE = "NoteOperateTopic";

    /**
     * Tag 标签：笔记发布
     */
    String TAG_NOTE_PUBLISH = "publishNote";

    /**
     * Tag 标签：笔记删除
     */
    String TAG_NOTE_DELETE = "deleteNote";
}
