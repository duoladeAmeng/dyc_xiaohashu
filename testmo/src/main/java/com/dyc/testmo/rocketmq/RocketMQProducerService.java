package com.dyc.testmo.rocketmq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * RocketMQ 消息生产者服务
 */
@Slf4j
@Service
public class RocketMQProducerService {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 发送同步消息
     *
     * @param topic   主题
     * @param message 消息内容
     * @return 发送结果
     */
    public String syncSend(String topic, String message) {
        log.info("发送同步消息 -> topic: {}, message: {}", topic, message);
        Message<String> msg = MessageBuilder.withPayload(message).build();
        rocketMQTemplate.syncSend(topic, msg);
        log.info("同步消息发送成功");
        return "syncSend success";
    }

    /**
     * 发送异步消息
     *
     * @param topic   主题
     * @param message 消息内容
     */
    public void asyncSend(String topic, String message) {
        log.info("发送异步消息 -> topic: {}, message: {}", topic, message);
        Message<String> msg = MessageBuilder.withPayload(message).build();
        rocketMQTemplate.asyncSend(topic, msg, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("异步消息发送成功: {}", sendResult);
            }

            @Override
            public void onException(Throwable e) {
                log.error("异步消息发送失败", e);
            }
        });
        log.info("异步消息已发送");
    }

    /**
     * 发送单向消息（不关心发送结果）
     *
     * @param topic   主题
     * @param message 消息内容
     */
    public void sendOneWay(String topic, String message) {
        log.info("发送单向消息 -> topic: {}, message: {}", topic, message);
        Message<String> msg = MessageBuilder.withPayload(message).build();
        rocketMQTemplate.sendOneWay(topic, msg);
        log.info("单向消息已发送");
    }

    /**
     * 发送带 Tag 的消息
     *
     * @param topic   主题
     * @param tag     标签
     * @param message 消息内容
     * @return 发送结果
     */
    public String syncSendWithTag(String topic, String tag, String message) {
        log.info("发送带 Tag 消息 -> topic: {}, tag: {}, message: {}", topic, tag, message);
        Message<String> msg = MessageBuilder.withPayload(message).build();
        rocketMQTemplate.syncSend(topic + ":" + tag, msg);
        log.info("带 Tag 消息发送成功");
        return "syncSend with tag success";
    }
}
