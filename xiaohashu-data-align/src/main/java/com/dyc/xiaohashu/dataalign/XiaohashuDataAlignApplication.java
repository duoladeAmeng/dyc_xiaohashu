package com.dyc.xiaohashu.dataalign;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.dyc.xiaohashu.dataalign.domain.mapper")
@EnableFeignClients(basePackages = "com.dyc.xiaohashu")
public class XiaohashuDataAlignApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaohashuDataAlignApplication.class, args);
    }

}
