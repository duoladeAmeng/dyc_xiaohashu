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
class RedisRoaring32Tests {

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    /**
     * Redis SET
     */
    private static final String SET_KEY =
            "test:int32:set";

    /**
     * Roaring Bitmap 32
     */
    private static final String ROARING_KEY =
            "test:int32:roaring";

    /**
     * Bloom Filter
     */
    private static final String BLOOM_KEY =
            "test:int32:bloom";

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
    // Roaring Bitmap 32 Script
    // =========================================================

    /**
     * R.SETBIT key offset value
     */
    private static final RedisScript<Long> R_SETBIT_SCRIPT =
            new DefaultRedisScript<>(
                    "return redis.call('R.SETBIT', KEYS[1], ARGV[1], ARGV[2])",
                    Long.class
            );

    /**
     * R.BITCOUNT key
     */
    private static final RedisScript<Long> R_BITCOUNT_SCRIPT =
            new DefaultRedisScript<>(
                    "return redis.call('R.BITCOUNT', KEYS[1])",
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
     * 32 位整数测试
     *
     * Redis SET
     * VS
     * Roaring Bitmap 32
     * VS
     * Bloom Filter
     *
     * 比较：
     *
     * 1. 内存占用
     * 2. 写入耗时
     * 3. 元素数量
     * 4. Bloom False Negative
     * 5. Bloom False Positive
     * 6. Bloom 实际误判率
     */
    @Test
    void testMemoryUsage() {

        // =====================================================
        // 1. 删除旧数据
        // =====================================================

        redisTemplate.delete(SET_KEY);
        redisTemplate.delete(ROARING_KEY);
        redisTemplate.delete(BLOOM_KEY);


        // =====================================================
        // 2. 生成完全相同的一批 32 位整数
        // =====================================================

        Set<Integer> ids =
                generateIds(COUNT);

        log.info("");
        log.info("生成 32 位整数数量: {}", ids.size());


        // =====================================================
        // 3. Redis SET
        // =====================================================

        long setStart =
                System.currentTimeMillis();

        for (Integer id : ids) {

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
        // 4. Roaring Bitmap 32
        // =====================================================

        long roaringStart =
                System.currentTimeMillis();

        for (Integer id : ids) {

            rSetBit(
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
        // 6. Bloom Filter 写入
        // =====================================================

        long bloomStart =
                System.currentTimeMillis();

        int bloomAddOneCount = 0;
        int bloomAddZeroCount = 0;

        for (Integer id : ids) {

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
        // 7. SET 元素数量
        // =====================================================

        Long setCount =
                redisTemplate
                        .opsForSet()
                        .size(SET_KEY);


        // =====================================================
        // 8. Roaring 元素数量
        // =====================================================

        Long roaringCount =
                rBitCount(
                        ROARING_KEY
                );


        // =====================================================
        // 9. Bloom False Negative 测试
        //
        // 已经插入 Bloom 的数据，
        // BF.EXISTS 应该全部返回 1
        // =====================================================

        int falseNegativeCount = 0;

        long existingQueryStart =
                System.currentTimeMillis();

        for (Integer id : ids) {

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

        long existingQueryTime =
                System.currentTimeMillis()
                        - existingQueryStart;


        // =====================================================
        // 10. 生成一批明确不存在的整数
        // =====================================================

        Set<Integer> nonExistingIds =
                generateNonExistingIds(
                        COUNT,
                        ids
                );


        // =====================================================
        // 11. Bloom False Positive 测试
        //
        // 没有插入 Bloom
        // 但是 BF.EXISTS 返回 1
        //
        // 就是假阳性
        // =====================================================

        int falsePositiveCount = 0;

        long nonExistingQueryStart =
                System.currentTimeMillis();

        for (Integer id : nonExistingIds) {

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

        long nonExistingQueryTime =
                System.currentTimeMillis()
                        - nonExistingQueryStart;


        // =====================================================
        // 12. Bloom 实际误判率
        // =====================================================

        double falsePositiveRate =
                falsePositiveCount
                        * 100.0
                        / nonExistingIds.size();


        // =====================================================
        // 13. 获取 Redis 实际内存占用
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
        // 14. 计算内存比例
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
        // 15. 输出测试结果
        // =====================================================

        log.info("");
        log.info("============================================================");
        log.info("32 位整数：Redis SET VS Roaring Bitmap VS Bloom Filter");
        log.info("============================================================");

        log.info(
                "测试整数数量               : {}",
                ids.size()
        );


        log.info("");
        log.info("---------------------- 元素数量 -----------------------------");

        log.info(
                "SET 元素数量               : {}",
                setCount
        );

        log.info(
                "Roaring 元素数量           : {}",
                roaringCount
        );


        log.info("");
        log.info("---------------------- 内存占用 -----------------------------");

        log.info(
                "SET 内存占用               : {} bytes",
                setMemory
        );

        log.info(
                "Roaring32 内存占用         : {} bytes",
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
                    "Roaring32 / SET            : {}%",
                    format(roaringRatio)
            );

            log.info(
                    "Roaring32 节省内存         : {}%",
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
                "Roaring32 写入耗时         : {} ms",
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
                existingQueryTime
        );

        log.info(
                "查询 {} 个不存在元素耗时   : {} ms",
                nonExistingIds.size(),
                nonExistingQueryTime
        );


        log.info("");
        log.info("---------------------- 平均每个元素内存 ---------------------");

        if (setMemory != null) {

            log.info(
                    "SET 平均每个元素           : {} bytes",
                    format(
                            setMemory * 1.0
                                    / ids.size()
                    )
            );
        }

        if (roaringMemory != null) {

            log.info(
                    "Roaring32 平均每个元素     : {} bytes",
                    format(
                            roaringMemory * 1.0
                                    / ids.size()
                    )
            );
        }

        if (bloomMemory != null) {

            log.info(
                    "Bloom 平均每个元素         : {} bytes",
                    format(
                            bloomMemory * 1.0
                                    / ids.size()
                    )
            );
        }

        log.info("============================================================");
        log.info("");
    }


    // =========================================================
    // ID Generator
    // =========================================================

    /**
     * 生成随机、唯一的正 32 位整数。
     *
     * 范围：
     *
     * 1 ~ 1,000,000,000
     *
     * 使用 Integer，
     * 因此这里测试的是 Java 正整数范围内的 32 位数据。
     */
    private Set<Integer> generateIds(
            int count) {

        Set<Integer> ids =
                new HashSet<>(
                        count * 2
                );

        int min =
                1;

        int max =
                1_000_000_000;

        while (ids.size() < count) {

            int id =
                    ThreadLocalRandom
                            .current()
                            .nextInt(
                                    min,
                                    max
                            );

            ids.add(id);
        }

        return ids;
    }


    /**
     * 生成明确没有插入的数据。
     *
     * 已插入范围：
     *
     * 1
     * ~
     * 999,999,999
     *
     * 未插入范围：
     *
     * 1,000,000,000
     * ~
     * 2,000,000,000
     */
    private Set<Integer> generateNonExistingIds(
            int count,
            Set<Integer> existingIds) {

        Set<Integer> ids =
                new HashSet<>(
                        count * 2
                );

        int min =
                1_000_000_000;

        int max =
                2_000_000_000;

        while (ids.size() < count) {

            int id =
                    ThreadLocalRandom
                            .current()
                            .nextInt(
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
    // Roaring Bitmap 32
    // =========================================================

    /**
     * R.SETBIT key offset value
     */
    private Long rSetBit(
            String key,
            int offset,
            int value) {

        return redisTemplate.execute(
                R_SETBIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(offset),
                String.valueOf(value)
        );
    }


    /**
     * R.BITCOUNT key
     */
    private Long rBitCount(
            String key) {

        return redisTemplate.execute(
                R_BITCOUNT_SCRIPT,
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
     * BF.ADD key item
     */
    private Long bfAdd(
            String key,
            int value) {

        return redisTemplate.execute(
                BF_ADD_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(value)
        );
    }


    /**
     * BF.EXISTS key item
     *
     * 1 = 可能存在
     * 0 = 一定不存在
     */
    private Long bfExists(
            String key,
            int value) {

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

    private String format(
            double value) {

        return String.format(
                "%.2f",
                value
        );
    }
}