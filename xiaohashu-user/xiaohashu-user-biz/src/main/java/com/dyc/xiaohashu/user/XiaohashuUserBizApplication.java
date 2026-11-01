package com.dyc.xiaohashu.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.dyc.xiaohashu.user.domain.mapper")
@EnableFeignClients(basePackages = "com.dyc.xiaohashu")
@ComponentScan({"com.dyc.xiaohashu.user", "com.dyc.xiaohashu.count"}) //  多模块扫描
public class XiaohashuUserBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaohashuUserBizApplication.class, args);
    }

}
