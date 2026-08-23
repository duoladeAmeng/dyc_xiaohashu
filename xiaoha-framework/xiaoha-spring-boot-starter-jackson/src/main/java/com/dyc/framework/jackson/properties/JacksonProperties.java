package com.dyc.framework.jackson.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Jackson 配置属性类
 * 允许用户通过 application.yml 自定义日期时间格式
 */
@ConfigurationProperties(prefix = "xiaoha.jackson")
public class JacksonProperties {

    /**
     * 日期时间格式，默认：yyyy-MM-dd HH:mm:ss
     */
    private String dateTimePattern = "yyyy-MM-dd HH:mm:ss";

    /**
     * 日期格式，默认：yyyy-MM-dd
     */
    private String datePattern = "yyyy-MM-dd";

    /**
     * 时间格式，默认：HH:mm:ss
     */
    private String timePattern = "HH:mm:ss";



    public String getDateTimePattern() {
        return dateTimePattern;
    }

    public void setDateTimePattern(String dateTimePattern) {
        this.dateTimePattern = dateTimePattern;
    }

    public String getDatePattern() {
        return datePattern;
    }

    public void setDatePattern(String datePattern) {
        this.datePattern = datePattern;
    }

    public String getTimePattern() {
        return timePattern;
    }

    public void setTimePattern(String timePattern) {
        this.timePattern = timePattern;
    }
}
