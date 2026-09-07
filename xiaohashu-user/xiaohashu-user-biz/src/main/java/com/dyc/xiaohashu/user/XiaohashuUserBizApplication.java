package com.dyc.xiaohashu.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Hello world!
 *
 */
@SpringBootApplication
@MapperScan("com.dyc.xiaohashu.user.domain.mapper")
@EnableFeignClients(basePackages = "com.dyc.xiaohashu.oss.api")
public class XiaohashuUserBizApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(XiaohashuUserBizApplication.class, args);
    }
}
