package com.dyc.xiaohashu.id.generator.enums;

/**
 * 对外暴露的 ID 生成器类型。
 */
public enum IdGeneratorType {

    /**
     * Snowflake 发号器，适合高吞吐、趋势递增场景。
     */
    SNOWFLAKE,

    /**
     * 数据库号段发号器。
     */
    SEGMENT,

    /**
     * 带预取链路的数据库号段发号器。
     */
    SEGMENT_CHAIN
}
