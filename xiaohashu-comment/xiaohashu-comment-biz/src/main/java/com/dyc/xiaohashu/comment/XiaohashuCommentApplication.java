package com.dyc.xiaohashu.comment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@MapperScan("com.dyc.xiaohashu.comment.domain.mapper")
@EnableRetry // 启用 Spring Retry
@EnableFeignClients(basePackages = "com.dyc.xiaohashu")
public class XiaohashuCommentApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaohashuCommentApplication.class, args);
    }

}
