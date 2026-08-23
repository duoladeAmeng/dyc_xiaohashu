package com.dyc.testmo.rocketmq;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RocketMQ 配置类
 * 配置已在 application.yml 中设置，此类用于扩展配置
 */
@Configuration
@Import(RocketMQAutoConfiguration.class)

public class RocketMQConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.producer.group}")
    private String producerGroup;

    /**
     * 可以在这里添加自定义的 RocketMQ 配置
     * 例如：消息过滤器、拦截器等
     */
}
