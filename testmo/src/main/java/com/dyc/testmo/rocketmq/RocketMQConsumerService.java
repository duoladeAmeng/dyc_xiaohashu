package com.dyc.testmo.rocketmq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

/**
 * RocketMQ 消息消费者服务
 * 消费 topic: test-topic 的消息
 */
@Slf4j
@Service
@RocketMQMessageListener(
        topic = "test-topic1",
        consumerGroup = "xiaohashu_consumer_group"
)
public class RocketMQConsumerService implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        log.info("收到消息: {}", message);
        // 在这里处理消息逻辑
        // 比如：保存到数据库、调用其他服务等
    }
}
