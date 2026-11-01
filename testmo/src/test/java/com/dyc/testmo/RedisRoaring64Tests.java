package com.dyc.testmo;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootTest
@Slf4j
class RedisRoaring64Tests {

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    /**
     * Redis SET
     */
    private static final String SET_KEY =
            "test:snowflake:set";

    /**
     * Roaring Bitmap 64
     */
    private static final String ROARING_KEY =
            "test:snowflake:roaring64";

    /**
     * Bloom Filter
     */
    private static final String BLOOM_KEY =
            "test:snowflake:bloom";

    /**
     * 测试数据量
     */
    private static final int COUNT = 10_000;

    /**
     * Bloom Filter 目标误判率
     *
     * 0.01 = 1%
     */
    private static final double BLOOM_ERROR_RATE = 0.01;


    // =========================================================
    // Roaring Bitmap Script
    // =========================================================

    /**
     * R64.SETBIT key offset value
     */
    private static final RedisScript<Long> R64_SETBIT_SCRIPT =
            new DefaultRedisScript<>(
                    "return redis.call('R64.SETBIT', KEYS[1], ARGV[1], ARGV[2])",
                    Long.class
            );

    /**
     * R64.BITCOUNT key
     */
    private static final RedisScript<Long> R64_BITCOUNT_SCRIPT =
            new DefaultRedisScript<>(
                    "return redis.call('R64.BITCOUNT', KEYS[1])",
                    Long.class
            );


    // =========================================================
    // RedisBloom Script
    // =========================================================

    /**
     * BF.RESERVE key error_rate capacity
     */
    private static final RedisScript<String> BF_RESERVE_SCRIPT =
            new DefaultRedisScript<>(
                    "return redis.call('BF.RESERVE', KEYS[1], ARGV[1], ARGV[2])",
                    String.class
            );

    /**
     * BF.ADD key item
     */
    private static final RedisScript<Long> BF_ADD_SCRIPT =
            new DefaultRedisScript<>(
                    "return redis.call('BF.ADD', KEYS[1], ARGV[1])",
                    Long.class
            );

    /**
     * BF.EXISTS key item
     */
    private static final RedisScript<Long> BF_EXISTS_SCRIPT =
            new DefaultRedisScript<>(
                    "return redis.call('BF.EXISTS', KEYS[1], ARGV[1])",
                    Long.class
            );


    // =========================================================
    // Redis Memory Script
    // =========================================================

    /**
     * MEMORY USAGE key
     */
    private static final RedisScript<Long> MEMORY_USAGE_SCRIPT =
            new DefaultRedisScript<>(
                    "return redis.call('MEMORY', 'USAGE', KEYS[1])",
                    Long.class
            );


    // =========================================================
    // Test
    // =========================================================

    /**
     * Redis SET
     * VS
     * Roaring Bitmap 64
     * VS
     * Bloom Filter
     *
     * 比较：
     *
     * 1. 实际 Redis 内存占用
     * 2. Roaring64 元素数量
     * 3. Bloom Filter 假阴性
     * 4. Bloom Filter 假阳性
     * 5. Bloom Filter 实际误判率
     */
    @Test
    void testMemoryUsage() {

        // =====================================================
        // 1. 清除旧数据
        // =====================================================

        redisTemplate.delete(SET_KEY);
        redisTemplate.delete(ROARING_KEY);
        redisTemplate.delete(BLOOM_KEY);


        // =====================================================
        // 2. 生成完全相同的一批 64 位随机 ID
        // =====================================================

        Set<Long> ids =
                generateIds(COUNT);

        log.info("");
        log.info("生成 ID 数量: {}", ids.size());


        // =====================================================
        // 3. Redis SET
        // =====================================================

        long setStart =
                System.currentTimeMillis();

        for (Long id : ids) {

            redisTemplate
                    .opsForSet()
                    .add(
                            SET_KEY,
                            String.valueOf(id)
                    );
        }

        long setWriteTime =
                System.currentTimeMillis()
                        - setStart;


        // =====================================================
        // 4. Roaring Bitmap 64
        // =====================================================

        long roaringStart =
                System.currentTimeMillis();

        for (Long id : ids) {

            r64SetBit(
                    ROARING_KEY,
                    id,
                    1
            );
        }

        long roaringWriteTime =
                System.currentTimeMillis()
                        - roaringStart;


        // =====================================================
        // 5. 初始化 Bloom Filter
        // =====================================================

        String reserveResult =
                bfReserve(
                        BLOOM_KEY,
                        BLOOM_ERROR_RATE,
                        COUNT
                );

        log.info(
                "BF.RESERVE 返回结果: {}",
                reserveResult
        );


        // =====================================================
        // 6. Bloom Filter 写入数据
        // =====================================================

        long bloomStart =
                System.currentTimeMillis();

        int bloomAddOneCount = 0;
        int bloomAddZeroCount = 0;

        for (Long id : ids) {

            Long result =
                    bfAdd(
                            BLOOM_KEY,
                            id
                    );

            if (result != null) {

                if (result == 1L) {
                    bloomAddOneCount++;
                } else {
                    bloomAddZeroCount++;
                }
            }
        }

        long bloomWriteTime =
                System.currentTimeMillis()
                        - bloomStart;


        // =====================================================
        // 7. 验证 SET 元素数量
        // =====================================================

        Long setCount =
                redisTemplate
                        .opsForSet()
                        .size(SET_KEY);


        // =====================================================
        // 8. 验证 Roaring64 元素数量
        // =====================================================

        Long roaringCount =
                r64BitCount(
                        ROARING_KEY
                );


        // =====================================================
        // 9. Bloom Filter
        //
        // 检查已经插入的数据
        //
        // Bloom Filter 理论上不应该出现假阴性：
        //
        // 已经插入
        // BF.EXISTS 却返回 0
        // =====================================================

        int falseNegativeCount = 0;

        long bloomExistingQueryStart =
                System.currentTimeMillis();

        for (Long id : ids) {

            Long exists =
                    bfExists(
                            BLOOM_KEY,
                            id
                    );

            if (exists == null
                    || exists == 0L) {

                falseNegativeCount++;
            }
        }

        long bloomExistingQueryTime =
                System.currentTimeMillis()
                        - bloomExistingQueryStart;


        // =====================================================
        // 10. 生成一批明确没有插入的数据
        // =====================================================

        Set<Long> nonExistingIds =
                generateNonExistingIds(
                        COUNT,
                        ids
                );


        // =====================================================
        // 11. Bloom Filter 假阳性测试
        //
        // 没插入
        // BF.EXISTS 却返回 1
        //
        // 这种情况就是 False Positive
        // =====================================================

        int falsePositiveCount = 0;

        long bloomNonExistingQueryStart =
                System.currentTimeMillis();

        for (Long id : nonExistingIds) {

            Long exists =
                    bfExists(
                            BLOOM_KEY,
                            id
                    );

            if (exists != null
                    && exists == 1L) {

                falsePositiveCount++;
            }
        }

        long bloomNonExistingQueryTime =
                System.currentTimeMillis()
                        - bloomNonExistingQueryStart;


        // =====================================================
        // 12. Bloom 实际误判率
        // =====================================================

        double falsePositiveRate =
                falsePositiveCount
                        * 100.0
                        / nonExistingIds.size();


        // =====================================================
        // 13. 查看实际 Redis 内存占用
        // =====================================================

        Long setMemory =
                memoryUsage(
                        SET_KEY
                );

        Long roaringMemory =
                memoryUsage(
                        ROARING_KEY
                );

        Long bloomMemory =
                memoryUsage(
                        BLOOM_KEY
                );


        // =====================================================
        // 14. 内存比例
        // =====================================================

        Double roaringRatio = null;
        Double roaringSaved = null;

        Double bloomRatio = null;
        Double bloomSaved = null;

        if (setMemory != null
                && setMemory > 0) {

            if (roaringMemory != null) {

                roaringRatio =
                        roaringMemory
                                * 100.0
                                / setMemory;

                roaringSaved =
                        100.0
                                - roaringRatio;
            }

            if (bloomMemory != null) {

                bloomRatio =
                        bloomMemory
                                * 100.0
                                / setMemory;

                bloomSaved =
                        100.0
                                - bloomRatio;
            }
        }


        // =====================================================
        // 15. 输出结果
        // =====================================================

        log.info("");
        log.info("============================================================");
        log.info("Redis SET VS Roaring Bitmap 64 VS Bloom Filter");
        log.info("============================================================");

        log.info(
                "测试 ID 数量               : {}",
                ids.size()
        );

        log.info("");
        log.info("---------------------- 元素数量 -----------------------------");

        log.info(
                "SET 元素数量               : {}",
                setCount
        );

        log.info(
                "Roaring64 元素数量         : {}",
                roaringCount
        );


        log.info("");
        log.info("---------------------- 内存占用 -----------------------------");

        log.info(
                "SET 内存占用               : {} bytes",
                setMemory
        );

        log.info(
                "Roaring64 内存占用         : {} bytes",
                roaringMemory
        );

        log.info(
                "Bloom 内存占用             : {} bytes",
                bloomMemory
        );


        log.info("");
        log.info("---------------------- 内存比例 -----------------------------");

        if (roaringRatio != null) {

            log.info(
                    "Roaring64 / SET            : {}%",
                    format(roaringRatio)
            );

            log.info(
                    "Roaring64 节省内存         : {}%",
                    format(roaringSaved)
            );
        }

        if (bloomRatio != null) {

            log.info(
                    "Bloom / SET                : {}%",
                    format(bloomRatio)
            );

            log.info(
                    "Bloom 节省内存             : {}%",
                    format(bloomSaved)
            );
        }


        log.info("");
        log.info("---------------------- 写入耗时 -----------------------------");

        log.info(
                "SET 写入耗时               : {} ms",
                setWriteTime
        );

        log.info(
                "Roaring64 写入耗时         : {} ms",
                roaringWriteTime
        );

        log.info(
                "Bloom 写入耗时             : {} ms",
                bloomWriteTime
        );


        log.info("");
        log.info("---------------------- Bloom ADD ----------------------------");

        log.info(
                "BF.ADD 返回 1              : {}",
                bloomAddOneCount
        );

        log.info(
                "BF.ADD 返回 0              : {}",
                bloomAddZeroCount
        );


        log.info("");
        log.info("---------------------- Bloom 正确性 -------------------------");

        log.info(
                "Bloom 目标误判率           : {}%",
                format(
                        BLOOM_ERROR_RATE
                                * 100
                )
        );

        log.info(
                "已插入元素测试数量         : {}",
                ids.size()
        );

        log.info(
                "Bloom False Negative       : {}",
                falseNegativeCount
        );

        log.info(
                "不存在元素测试数量         : {}",
                nonExistingIds.size()
        );

        log.info(
                "Bloom False Positive       : {}",
                falsePositiveCount
        );

        log.info(
                "Bloom 实际误判率           : {}%",
                format(
                        falsePositiveRate
                )
        );


        log.info("");
        log.info("---------------------- Bloom 查询耗时 -----------------------");

        log.info(
                "查询 {} 个已存在元素耗时   : {} ms",
                ids.size(),
                bloomExistingQueryTime
        );

        log.info(
                "查询 {} 个不存在元素耗时   : {} ms",
                nonExistingIds.size(),
                bloomNonExistingQueryTime
        );

        log.info("============================================================");
        log.info("");
    }


    // =========================================================
    // ID Generator
    // =========================================================

    /**
     * 生成随机的 64 位大整数。
     *
     * 模拟 Snowflake ID 数值范围，
     * 同时保证当前测试内不重复。
     */
    private Set<Long> generateIds(
            int count) {

        Set<Long> ids =
                new HashSet<>(
                        count * 2
                );

        long min =
                1_000_000_000_000_000_000L;

        long max =
                2_000_000_000_000_000_000L;

        while (ids.size() < count) {

            long id =
                    ThreadLocalRandom
                            .current()
                            .nextLong(
                                    min,
                                    max
                            );

            ids.add(id);
        }

        return ids;
    }


    /**
     * 生成一批明确没有插入 Bloom Filter 的 ID。
     *
     * 插入数据范围：
     *
     * 1_000_000_000_000_000_000
     * ~
     * 2_000_000_000_000_000_000
     *
     * 未插入测试数据范围：
     *
     * 2_000_000_000_000_000_001
     * ~
     * 3_000_000_000_000_000_000
     */
    private Set<Long> generateNonExistingIds(
            int count,
            Set<Long> existingIds) {

        Set<Long> ids =
                new HashSet<>(
                        count * 2
                );

        long min =
                2_000_000_000_000_000_001L;

        long max =
                3_000_000_000_000_000_000L;

        while (ids.size() < count) {

            long id =
                    ThreadLocalRandom
                            .current()
                            .nextLong(
                                    min,
                                    max
                            );

            if (!existingIds.contains(id)) {
                ids.add(id);
            }
        }

        return ids;
    }


    // =========================================================
    // Roaring Bitmap
    // =========================================================

    /**
     * R64.SETBIT key offset value
     */
    private Long r64SetBit(
            String key,
            long offset,
            int value) {

        return redisTemplate.execute(
                R64_SETBIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(offset),
                String.valueOf(value)
        );
    }


    /**
     * R64.BITCOUNT key
     */
    private Long r64BitCount(
            String key) {

        return redisTemplate.execute(
                R64_BITCOUNT_SCRIPT,
                Collections.singletonList(key)
        );
    }


    // =========================================================
    // Bloom Filter
    // =========================================================

    /**
     * 创建 Bloom Filter。
     *
     * BF.RESERVE key error_rate capacity
     *
     * @param key       Redis Key
     * @param errorRate 误判率，例如 0.01 = 1%
     * @param capacity  预计元素数量
     */
    private String bfReserve(
            String key,
            double errorRate,
            long capacity) {

        return redisTemplate.execute(
                BF_RESERVE_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(errorRate),
                String.valueOf(capacity)
        );
    }


    /**
     * 添加元素到 Bloom Filter。
     *
     * BF.ADD key item
     *
     * 返回：
     *
     * 1 = Bloom 判断该元素之前不存在
     * 0 = Bloom 判断该元素可能已经存在
     */
    private Long bfAdd(
            String key,
            long value) {

        return redisTemplate.execute(
                BF_ADD_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(value)
        );
    }


    /**
     * 判断元素是否可能存在。
     *
     * BF.EXISTS key item
     *
     * 返回：
     *
     * 1 = 可能存在
     * 0 = 一定不存在
     */
    private Long bfExists(
            String key,
            long value) {

        return redisTemplate.execute(
                BF_EXISTS_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(value)
        );
    }


    // =========================================================
    // Redis Memory
    // =========================================================

    /**
     * MEMORY USAGE key
     */
    private Long memoryUsage(
            String key) {

        return redisTemplate.execute(
                MEMORY_USAGE_SCRIPT,
                Collections.singletonList(key)
        );
    }


    // =========================================================
    // Format
    // =========================================================

    /**
     * 保留两位小数。
     */
    private String format(
            double value) {

        return String.format(
                "%.2f",
                value
        );
    }
}