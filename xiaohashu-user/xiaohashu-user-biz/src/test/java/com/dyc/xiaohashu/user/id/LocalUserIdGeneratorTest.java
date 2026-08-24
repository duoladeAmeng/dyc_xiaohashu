package com.dyc.xiaohashu.user.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalUserIdGeneratorTest {

    private final LocalUserIdGenerator generator = new LocalUserIdGenerator();

    @Test
    void shouldGenerateIncreasingUserIds() {
        Long first = generator.nextUserId();
        Long second = generator.nextUserId();

        assertTrue(second > first);
    }

    @Test
    void shouldGenerateValidXiaohashuIds() {
        String first = generator.nextXiaohashuId();
        String second = generator.nextXiaohashuId();

        assertNotEquals(first, second);
        assertTrue(first.matches("^[A-Z0-9]{6,15}$"));
    }
}
