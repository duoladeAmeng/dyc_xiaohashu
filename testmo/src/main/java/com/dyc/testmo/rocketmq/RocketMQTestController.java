package com.dyc.testmo.rocketmq;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * RocketMQ 测试接口
 */
@RestController
@RequestMapping("/mq")
@RequiredArgsConstructor
public class RocketMQTestController {

    private final RocketMQProducerService producerService;

    /**
     * 测试发送同步消息
     * 访问: http://127.0.0.1:8080/mq/send?message=hello
     *
     * @param message 消息内容
     * @return 发送结果
     */
    @GetMapping("/send")
    public String send(@RequestParam(defaultValue = "Hello RocketMQ!") String message) {
        return producerService.syncSend("test-topic1", message);
    }

    /**
     * 测试发送异步消息
     * 访问: http://127.0.0.1:8080/mq/send-async?message=hello
     *
     * @param message 消息内容
     * @return 发送结果
     */
    @GetMapping("/send-async")
    public String sendAsync(@RequestParam(defaultValue = "Hello Async RocketMQ!") String message) {
        producerService.asyncSend("test-topic", message);
        return "async message sent";
    }

    /**
     * 测试发送单向消息
     * 访问: http://127.0.0.1:8080/mq/send-oneway?message=hello
     *
     * @param message 消息内容
     * @return 发送结果
     */
    @GetMapping("/send-oneway")
    public String sendOneWay(@RequestParam(defaultValue = "Hello OneWay RocketMQ!") String message) {
        producerService.sendOneWay("test-topic", message);
        return "one-way message sent";
    }

    /**
     * 测试发送带 Tag 的消息
     * 访问: http://127.0.0.1:8080/mq/send-tag?message=hello&tag=tagA
     *
     * @param message 消息内容
     * @param tag     标签
     * @return 发送结果
     */
    @GetMapping("/send-tag")
    public String sendWithTag(
            @RequestParam(defaultValue = "Hello Tag RocketMQ!") String message,
            @RequestParam(defaultValue = "tagA") String tag) {
        return producerService.syncSendWithTag("test-topic", tag, message);
    }
}
