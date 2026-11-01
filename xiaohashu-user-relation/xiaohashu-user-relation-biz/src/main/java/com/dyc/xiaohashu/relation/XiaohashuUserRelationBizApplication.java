package com.dyc.xiaohashu.relation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.dyc.xiaohashu.relation.domain.mapper")
@EnableFeignClients(basePackages = "com.dyc.xiaohashu")
public class XiaohashuUserRelationBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaohashuUserRelationBizApplication.class, args);
    }

}
