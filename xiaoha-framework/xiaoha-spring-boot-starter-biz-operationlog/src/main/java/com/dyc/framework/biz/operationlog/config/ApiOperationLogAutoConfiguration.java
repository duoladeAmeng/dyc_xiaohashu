package com.dyc.framework.biz.operationlog.config;

import com.dyc.framework.biz.operationlog.aspect.ApiOperationLogAspect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ApiOperationLogAutoConfiguration {
    private final ObjectMapper objectMapper;

    public ApiOperationLogAutoConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean public ApiOperationLogAspect apiOperationLogAutoConfiguration(){
        return new ApiOperationLogAspect(objectMapper);
    }
}
