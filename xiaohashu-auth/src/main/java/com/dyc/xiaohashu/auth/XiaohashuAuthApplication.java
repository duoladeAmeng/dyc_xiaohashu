package com.dyc.xiaohashu.auth;

import com.dyc.xiaohashu.user.api.UserFeignApi;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(clients = UserFeignApi.class)
public class XiaohashuAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaohashuAuthApplication.class, args);
    }

}
