package com.dyc.xiaohashu.user.id;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地 ID 生成适配，后续可替换为正式分布式 ID 服务。
 */
@Component
public class LocalUserIdGenerator implements UserIdGenerator {

    private static final int MAX_SEQUENCE = 999;
    private final AtomicInteger sequence = new AtomicInteger(0);

    @Override
    public Long nextUserId() {
        long millis = System.currentTimeMillis();
        int next = sequence.updateAndGet(value -> value >= MAX_SEQUENCE ? 0 : value + 1);
        return millis * 1000 + next;
    }

    @Override
    public String nextXiaohashuId() {
        return Long.toString(nextUserId(), 36).toUpperCase(Locale.ROOT);
    }
}
