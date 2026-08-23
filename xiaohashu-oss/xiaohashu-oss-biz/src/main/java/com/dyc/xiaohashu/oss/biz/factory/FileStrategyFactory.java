package com.dyc.xiaohashu.oss.biz.factory;

import com.dyc.xiaohashu.oss.biz.strategy.FileStrategy;
import com.dyc.xiaohashu.oss.biz.strategy.impl.AliyunOSSFileStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RefreshScope
public class FileStrategyFactory {

    @Value("${storage.type}")
    private String strategyType;

    @Bean
    @RefreshScope
    public FileStrategy getFileStrategy() {
        if (strategyType.equals("aliyun")) {
            return new AliyunOSSFileStrategy();
        }
        throw new IllegalArgumentException("不可用的存储类型");
    }

}
