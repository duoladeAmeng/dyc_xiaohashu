# 短信验证码功能开发记录

## 1. 需求概述

在 auth 微服务中实现发送短信验证码功能，验证码存储在 Redis 中，支持验证码发送和校验。

## 2. 技术选型

- **存储层**：Redis（验证码缓存）
- **框架**：Spring Boot 3.0.2 + Spring Data Redis
- **校验**：Jakarta Validation
- **异常处理**：BizException + GlobalExceptionHandler

## 3. 开发过程

### 3.1 添加 Redis 依赖

在 `xiaohashu-auth/pom.xml` 中添加 Spring Data Redis 依赖：

```xml
<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 3.2 配置 Redis 连接

在 `application-dev.yml` 中添加 Redis 配置：

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
```

### 3.3 添加业务异常码

在 `ResponseCodeEnum` 中添加验证码相关错误码：

```java
// ----------- 业务异常状态码 -----------
VERIFICATION_CODE_SEND_FAIL("AUTH-20001", "验证码发送失败"),
VERIFICATION_CODE_EXPIRED("AUTH-20002", "验证码已过期"),
VERIFICATION_CODE_ERROR("AUTH-20003", "验证码错误"),
```

### 3.4 创建 Service 接口

**文件**：`VerificationCodeService.java`

```java
package com.dyc.xiaohashu.auth.service;

/**
 * 验证码服务接口
 */
public interface VerificationCodeService {

    /**
     * 发送验证码
     *
     * @param phone 手机号
     */
    void sendCode(String phone);

    /**
     * 校验验证码
     *
     * @param phone 手机号
     * @param code  验证码
     * @throws BizException 验证码过期或错误时抛出异常
     */
    void verifyCode(String phone, String code);
}
```

### 3.5 创建 Service 实现类

**文件**：`VerificationCodeServiceImpl.java`

```java
package com.dyc.xiaohashu.auth.service;

import com.dyc.framework.common.exception.BizException;
import com.dyc.xiaohashu.auth.enums.ResponseCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    /**
     * 验证码 KEY 前缀
     */
    private static final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";

    /**
     * 验证码过期时间（分钟）
     */
    private static final long CODE_EXPIRE_MINUTES = 5;

    /**
     * 验证码长度
     */
    private static final int CODE_LENGTH = 6;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void sendCode(String phone) {
        // 1. 生成验证码
        String code = generateCode();

        // 2. 存储到 Redis
        String key = VERIFICATION_CODE_KEY_PREFIX + phone;
        stringRedisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 3. 发送短信（TODO: 对接实际短信服务）
        log.info("验证码已生成，手机号: {}, 验证码: {}", phone, code);
    }

    @Override
    public void verifyCode(String phone, String code) {
        String key = VERIFICATION_CODE_KEY_PREFIX + phone;
        String cachedCode = stringRedisTemplate.opsForValue().get(key);

        if (cachedCode == null) {
            log.warn("验证码已过期或不存在，手机号: {}", phone);
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_EXPIRED);
        }

        if (!cachedCode.equals(code)) {
            log.warn("验证码校验失败，手机号: {}, 输入验证码: {}, 正确验证码: {}", phone, code, cachedCode);
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
        }

        // 验证成功，删除验证码
        stringRedisTemplate.delete(key);
    }

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
```

### 3.6 创建 DTO

**文件**：`SendCodeRequest.java`

```java
package com.dyc.xiaohashu.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendCodeRequest {

    @NotBlank(message = "手机号不能为空")
    private String phone;
}
```

### 3.7 创建 Controller

**文件**：`VerificationCodeController.java`

```java
package com.dyc.xiaohashu.auth.controller;

import com.dyc.framework.biz.operationlog.aspect.ApiOperationLog;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.auth.dto.SendCodeReq;
import com.dyc.xiaohashu.auth.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class VerificationCodeController {

    private final VerificationCodeService verificationCodeService;

    @ApiOperationLog(description = "发送验证码")
    @PostMapping("/verification/code/send")
    public Response<Void> sendCode(@RequestBody @Validated SendCodeRequest request) {
        verificationCodeService.sendCode(request.getPhone());
        return Response.success();
    }
}
```

## 4. 文件清单

| 文件 | 说明 |
|------|------|
| `xiaohashu-auth/pom.xml` | 添加 Redis 依赖 |
| `application-dev.yml` | 添加 Redis 配置 |
| `ResponseCodeEnum.java` | 添加验证码错误码 |
| `VerificationCodeService.java` | 验证码服务接口 |
| `VerificationCodeServiceImpl.java` | 验证码服务实现 |
| `SendCodeRequest.java` | 发送验证码请求 DTO |
| `VerificationCodeController.java` | 验证码控制器 |

## 5. 接口说明

### 发送验证码

- **请求路径**：`POST /auth/verification/code/send`
- **请求体**：
  ```json
  {
    "phone": "13800138000"
  }
  ```
- **成功响应**：
  ```json
  {
    "success": true,
    "data": null
  }
  ```
- **失败响应**（手机号为空）：
  ```json
  {
    "success": false,
    "errorCode": "AUTH-10001",
    "message": "phone 手机号不能为空, 当前值: 'null'; "
  }
  ```

## 6. Redis 数据结构

- **Key**：`verification_code:{phone}`
- **Value**：6位数字验证码
- **过期时间**：5分钟

示例：
```
key:   verification_code:13800138000
value: 384927
TTL:   300s
```

## 7. 待优化项

- [ ] 对接实际短信服务（阿里云/腾讯云短信）
- [ ] 添加发送频率限制（防刷）
- [ ] 添加验证码发送次数限制
- [ ] 考虑使用验证码模板
