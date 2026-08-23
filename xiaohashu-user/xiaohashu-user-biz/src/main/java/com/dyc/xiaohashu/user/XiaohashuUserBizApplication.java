package com.dyc.xiaohashu.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hello world!
 *
 */
@SpringBootApplication
@MapperScan("com.dyc.xiaohashu.user.domain.mapper")
public class XiaohashuUserBizApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(XiaohashuUserBizApplication.class, args);
    }
}
