package com.dyc.xiaohashu.auth.service;

import com.dyc.framework.common.exception.BizException;
import com.dyc.xiaohashu.auth.constant.RedisKeyConstants;
import com.dyc.xiaohashu.auth.enums.ResponseCodeEnum;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 验证码过期时间（分钟）
     */
    private static final long CODE_EXPIRE_MINUTES = 3;

    /**
     * 验证码长度
     */
    private static final int CODE_LENGTH = 6;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void sendCode(String phone) {
        // 1. 生成验证码
        String code = generateCode();

        // 2. 构建验证码 redis key
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);

        // 3.判断是否已发送验证码
        boolean isSent = stringRedisTemplate.hasKey(key);
        if (isSent) {
            // 若之前发送的验证码未过期，则提示发送频繁
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }

        // 调用第三方短信发送服务
        threadPoolTaskExecutor.submit(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // 发送短信（TODO: 对接实际短信服务）
            log.info("验证码已生成，手机号: {}, 验证码: {}", phone, code);
        });

        // 存储验证码到 redis, 并设置过期时间为 3 分钟
        stringRedisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

    }

    @Override
    public void verifyCode(String phone, String code) {
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);
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
