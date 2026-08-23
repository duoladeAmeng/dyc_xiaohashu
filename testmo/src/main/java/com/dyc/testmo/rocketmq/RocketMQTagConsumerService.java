package com.dyc.testmo.rocketmq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

/**
 * RocketMQ 消息消费者服务 - 消费带 Tag 的消息
 * 消费 topic: test-topic, tag: tagA 的消息
 */
@Slf4j
@Service
@RocketMQMessageListener(
        topic = "test-topic",
        selectorExpression = "tagA",
        consumerGroup = "xiaohashu_consumer_tag_group"
)
public class RocketMQTagConsumerService implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        log.info("收到带 Tag 的消息: {}", message);
        // 在这里处理带 Tag 的消息逻辑
    }
}
