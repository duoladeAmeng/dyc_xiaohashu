package com.dyc.xiaohashu.id.generator.core.segment.grouped;

public interface Grouped {

    default GroupedKey group() {
        return GroupedKey.NEVER;
    }
}
