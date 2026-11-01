# Snowflake ID 生成器详解

## 一、整体思路

### 1.1 什么是 Snowflake

Snowflake 是 Twitter 开源的分布式 ID 生成算法，核心思想是：

**一个 64 位的 ID = 时间戳 + 机器 ID + 序列号**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Snowflake ID 结构 (64 bits)                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  0                    41                     51                    63       │
│  ├────────────────────┼────────────────────┼────────────────────┤          │
│  │   时间戳 (41 bits) │  机器 ID (10 bits)  │  序列号 (12 bits)  │          │
│  │                    │                    │                    │          │
│  │  69 年可用         │  1024 台机器        │  4096/ms           │          │
│  └────────────────────┴────────────────────┴────────────────────┘          │
│                                                                             │
│  注意：最高位是符号位，固定为 0，所以实际可用 63 位                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 核心设计目标

1. **全局唯一**：不同机器生成的 ID 不重复
2. **有序递增**：ID 随时间单调递增
3. **高性能**：本地生成，无需网络调用
4. **可配置**：位数分配可调整

### 1.3 整体流程

```
调用 generate()
    │
    ├── 1. 获取当前时间戳
    │       long currentTimestamp = getCurrentTime()
    │
    ├── 2. 检查时钟回拨
    │       if (currentTimestamp < lastTimestamp)
    │           throw ClockBackwardsException
    │
    ├── 3. 时间前进时重置序列号
    │       if (currentTimestamp > lastTimestamp && sequence >= threshold)
    │           sequence = 0
    │
    ├── 4. 自增序列号
    │       sequence = (sequence + 1) & maxSequence
    │
    ├── 5. 序列号溢出处理
    │       if (sequence == 0)
    │           currentTimestamp = nextTime()  // 等待下一毫秒
    │
    ├── 6. 更新最后时间戳
    │       lastTimestamp = currentTimestamp
    │
    ├── 7. 检查时间戳溢出
    │       if (diffTimestamp > maxTimestamp)
    │           throw TimestampOverflowException
    │
    └── 8. 位拼接返回
            return diffTimestamp << timestampLeft
                 | machineId << machineLeft
                 | sequence
```

---

## 二、具体实现

### 2.1 SnowflakeIdGenerator 类

**文件位置**：`core/snowflake/SnowflakeIdGenerator.java`

#### 2.1.1 核心常量

```java
public static final int TOTAL_BIT = 63;                    // 总位数 (不含符号位)
public static final int DEFAULT_TIMESTAMP_BIT = 41;        // 默认时间戳位数
public static final int DEFAULT_MACHINE_BIT = 10;          // 默认机器 ID 位数
public static final int DEFAULT_SEQUENCE_BIT = 12;         // 默认序列号位数

// 默认序列号重置阈值：2^(12-1) - 1 = 2047
public static final long DEFAULT_SEQUENCE_RESET_THRESHOLD = ~(-1L << (DEFAULT_SEQUENCE_BIT - 1));
```

**为什么是 63 位？**
- Java 的 long 是 64 位有符号整数
- 最高位是符号位，必须为 0
- 所以实际可用 63 位

#### 2.1.2 构造函数详解

```java
public SnowflakeIdGenerator(long epoch, int timestampBit, int machineBit, 
                           int sequenceBit, int machineId, long sequenceResetThreshold) {
    // 1. 校验位数总和
    if ((timestampBit + machineBit + sequenceBit) > TOTAL_BIT) {
        throw new IllegalArgumentException("total bit can't be greater than TOTAL_BIT[63] .");
    }
    
    this.epoch = epoch;
    this.timestampBit = timestampBit;
    this.machineBit = machineBit;
    this.sequenceBit = sequenceBit;
    
    // 2. 计算最大值
    this.maxTimestamp = ~(-1L << timestampBit);      // 2^41 - 1
    this.maxSequence = ~(-1L << sequenceBit);         // 2^12 - 1
    this.maxMachineId = ~(-1 << machineBit);          // 2^10 - 1
    
    // 3. 校验 machineId 范围
    if (machineId > this.maxMachineId || machineId < 0) {
        throw new IllegalArgumentException(...);
    }
    
    // 4. 计算位移量
    this.machineLeft = sequenceBit;                    // machineId 左移位数
    this.timestampLeft = this.machineLeft + machineBit; // timestamp 左移位数
    
    this.machineId = machineId;
    this.sequenceResetThreshold = sequenceResetThreshold;
}
```

**位移量计算示例**：
```
假设：timestampBit=41, machineBit=10, sequenceBit=12

machineLeft = 12                    // machineId 左移 12 位
timestampLeft = 12 + 10 = 22       // timestamp 左移 22 位

ID 拼接：
timestamp (41 bits) | machineId (10 bits) | sequence (12 bits)
      ↓                    ↓                    ↓
左移 22 位             左移 12 位            不动
```

#### 2.1.3 generate() 方法详解

```java
@Override
public synchronized long generate() {
    // 1. 获取当前时间戳
    long currentTimestamp = getCurrentTime();
    
    // 2. 检查时钟回拨
    if (currentTimestamp < lastTimestamp) {
        throw new ClockBackwardsException(lastTimestamp, currentTimestamp);
    }

    // 3. 时间前进时，检查是否需要重置序列号
    //    当 sequence >= threshold 且时间前进时，重置为 0
    //    目的：优化分片均匀性
    if (currentTimestamp > lastTimestamp && sequence >= sequenceResetThreshold) {
        sequence = 0L;
    }

    // 4. 自增序列号 (位运算：+1 后取低 12 位)
    sequence = (sequence + 1) & maxSequence;
    
    // 5. 序列号溢出 (从 maxSequence 变成 0)
    if (sequence == 0L) {
        // 等待下一毫秒
        currentTimestamp = nextTime();
    }

    // 6. 更新最后时间戳
    lastTimestamp = currentTimestamp;
    
    // 7. 计算相对于 epoch 的时间差
    long diffTimestamp = currentTimestamp - epoch;
    
    // 8. 检查时间戳是否溢出
    if (diffTimestamp > maxTimestamp) {
        throw new TimestampOverflowException(epoch, diffTimestamp, maxTimestamp);
    }
    
    // 9. 位拼接返回 ID
    return diffTimestamp << timestampLeft     // 时间戳左移到高位
         | machineId << machineLeft           // 机器 ID 左移到中间
         | sequence;                          // 序列号在低位
}
```

**关键点解释**：

1. **synchronized**：保证线程安全，同一时刻只有一个线程生成 ID
2. **sequenceResetThreshold**：优化分片均匀性，避免 sequence 一直递增导致分片不均
3. **& maxSequence**：位运算取低 N 位，等价于 `% (maxSequence + 1)`
4. **nextTime()**：busy spin 等待，避免线程切换开销

#### 2.1.4 nextTime() 方法详解

```java
protected long nextTime() {
    long time = getCurrentTime();
    // busy spin：不断检查时间是否超过 lastTimestamp
    while (time <= lastTimestamp) {
        time = getCurrentTime();
    }
    return time;
}
```

**为什么用 busy spin？**
- 时间回拨通常很短 (毫秒级)
- 线程切换开销 (微秒级) 比忙等更大
- `Thread.onSpinWait()` 提示 CPU 优化

### 2.2 ClockSyncSnowflakeIdGenerator 类

**文件位置**：`core/snowflake/ClockSyncSnowflakeIdGenerator.java`

#### 2.2.1 设计目的

当检测到时钟回拨时，**自动等待并重试**，而不是直接抛异常。

#### 2.2.2 实现详解

```java
public class ClockSyncSnowflakeIdGenerator implements IdGenerator {
    
    private final SnowflakeIdGenerator actual;                      // 被装饰的生成器
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;  // 时钟同步器

    @Override
    public long generate() {
        try {
            // 1. 尝试生成
            return actual.generate();
        } catch (ClockBackwardsException exception) {
            // 2. 捕获时钟回拨异常
            log.warn(exception.getMessage(), exception);
            
            // 3. 同步等待
            clockBackwardsSynchronizer.syncUninterruptibly(actual.getLastTimestamp());
            
            // 4. 重试生成
            return actual.generate();
        }
    }
}
```

**流程图**：
```
调用 generate()
    │
    ├── 成功 → 返回 ID
    │
    └── 抛出 ClockBackwardsException
        │
        ├── 1. 记录日志
        │
        ├── 2. 调用 syncUninterruptibly(lastTimestamp)
        │   │
        │   ├── 计算回拨时间：backwards = lastTimestamp - currentTime
        │   │
        │   ├── 回拨 ≤ spinThreshold (1ms)
        │   │   └── while (backwards > 0) Thread.onSpinWait()
        │   │
        │   ├── spinThreshold < 回拨 ≤ brokenThreshold (500ms)
        │   │   └── Thread.sleep(backwards)
        │   │
        │   └── 回拨 > brokenThreshold
        │       └── throw ClockTooManyBackwardsException
        │
        └── 3. 重试 generate()
```

### 2.3 ClockBackwardsSynchronizer 接口

**文件位置**：`core/machine/ClockBackwardsSynchronizer.java`

```java
public interface ClockBackwardsSynchronizer {
    
    // 默认实现
    ClockBackwardsSynchronizer DEFAULT = new DefaultClockBackwardsSynchronizer();
    
    // 同步方法 (可中断)
    void sync(long lastTimestamp) throws InterruptedException, ClockTooManyBackwardsException;
    
    // 不可中断同步方法
    void syncUninterruptibly(long lastTimestamp) throws ClockTooManyBackwardsException;
    
    // 计算回拨时间
    static long getBackwardsTimeStamp(long lastTimestamp) {
        return lastTimestamp - System.currentTimeMillis();
    }
}
```

### 2.4 DefaultClockBackwardsSynchronizer 类

**文件位置**：`core/machine/DefaultClockBackwardsSynchronizer.java`

#### 2.4.1 阈值配置

```java
public static final int DEFAULT_SPIN_THRESHOLD = 1;    // spin 等待阈值：1ms
public static final int DEFAULT_BROKEN_THRESHOLD = 500; // 抛异常阈值：500ms
```

#### 2.4.2 sync() 方法详解

```java
@Override
public void sync(long lastTimestamp) throws InterruptedException, ClockTooManyBackwardsException {
    // 1. 计算回拨时间
    long backwards = ClockBackwardsSynchronizer.getBackwardsTimeStamp(lastTimestamp);
    
    // 2. 没有回拨，直接返回
    if (backwards <= 0) {
        return;
    }
    
    log.warn("Detected clock backwards: {} ms, lastTimestamp: {}.", backwards, lastTimestamp);
    
    // 3. 小回拨：spin 等待
    if (backwards <= spinThreshold) {
        while (ClockBackwardsSynchronizer.getBackwardsTimeStamp(lastTimestamp) > 0) {
            Thread.onSpinWait();  // 提示 CPU 优化
        }
        return;
    }
    
    // 4. 大回拨：抛异常
    if (backwards > brokenThreshold) {
        throw new ClockTooManyBackwardsException(lastTimestamp, System.currentTimeMillis(), brokenThreshold);
    }
    
    // 5. 中等回拨：sleep 等待
    TimeUnit.MILLISECONDS.sleep(backwards);
}
```

**决策流程**：
```
计算回拨时间 backwards
    │
    ├── backwards ≤ 0
    │   └── 返回 (无回拨)
    │
    ├── backwards ≤ 1ms
    │   └── spin 等待
    │
    ├── 1ms < backwards ≤ 500ms
    │   └── sleep(backwards)
    │
    └── backwards > 500ms
        └── 抛异常
```

#### 2.4.3 syncUninterruptibly() 方法详解

```java
@Override
public void syncUninterruptibly(long lastTimestamp) throws ClockTooManyBackwardsException {
    try {
        sync(lastTimestamp);
    } catch (InterruptedException e) {
        // 恢复中断状态
        log.error("Thread interrupted during sync - lastTimestamp:[{}]. Restoring interrupt status.", lastTimestamp, e);
        Thread.currentThread().interrupt();
        throw new IdGeneratorException(e);
    }
}
```

**为什么需要这个方法？**
- `sync()` 可能抛出 `InterruptedException`
- 很多场景不允许中断 (如 ID 生成)
- 这个方法捕获中断，恢复中断状态，然后抛出运行时异常

---

## 三、异常类详解

### 3.1 ClockBackwardsException

**文件位置**：`core/snowflake/exception/ClockBackwardsException.java`

```java
public class ClockBackwardsException extends IdGeneratorException {
    private final long lastTimestamp;
    private final long currentTimestamp;
    
    public ClockBackwardsException(long lastTimestamp, long currentTimestamp) {
        super(String.format("Clock moved backwards. Refusing to generate id for %d milliseconds.",
                lastTimestamp - currentTimestamp));
        this.lastTimestamp = lastTimestamp;
        this.currentTimestamp = currentTimestamp;
    }
}
```

### 3.2 TimestampOverflowException

**文件位置**：`core/snowflake/exception/TimestampOverflowException.java`

```java
public class TimestampOverflowException extends IdGeneratorException {
    private final long epoch;
    private final long diffTimestamp;
    private final long maxTimestamp;
    
    public TimestampOverflowException(long epoch, long diffTimestamp, long maxTimestamp) {
        super(String.format("Timestamp overflow. epoch:[%s], diffTimestamp:[%s], maxTimestamp:[%s].",
                epoch, diffTimestamp, maxTimestamp));
        this.epoch = epoch;
        this.diffTimestamp = diffTimestamp;
        this.maxTimestamp = maxTimestamp;
    }
}
```

### 3.3 ClockTooManyBackwardsException

**文件位置**：`core/snowflake/exception/ClockTooManyBackwardsException.java`

```java
public class ClockTooManyBackwardsException extends IdGeneratorException {
    private final long lastTimestamp;
    private final long currentTimestamp;
    private final int brokenThreshold;
    
    public ClockTooManyBackwardsException(long lastTimestamp, long currentTimestamp, int brokenThreshold) {
        super(String.format("Clock moved too many backwards. lastTimestamp:[%s], currentTimestamp:[%s], brokenThreshold:[%s].",
                lastTimestamp, currentTimestamp, brokenThreshold));
        this.lastTimestamp = lastTimestamp;
        this.currentTimestamp = currentTimestamp;
        this.brokenThreshold = brokenThreshold;
    }
}
```

---

## 四、场景推演

### 4.1 场景 1：正常生成

```
初始状态：
  lastTimestamp = -1
  sequence = 0
  epoch = 1609459200000 (2021-01-01)

调用 generate()：
  currentTimestamp = 1700000000000 (2023-11-14)
  
  1. currentTimestamp > lastTimestamp ✓
  2. sequence = (0 + 1) & 4095 = 1
  3. sequence != 0，不等待
  4. lastTimestamp = 1700000000000
  5. diffTimestamp = 1700000000000 - 1609459200000 = 90540800000
  6. diffTimestamp << 22 | machineId << 12 | 1
  → 返回 ID
```

### 4.2 场景 2：同一毫秒内多次调用

```
第 1 次调用：
  sequence = (0 + 1) & 4095 = 1
  → 返回 ID1

第 2 次调用：
  sequence = (1 + 1) & 4095 = 2
  → 返回 ID2

... (同一毫秒内)

第 4096 次调用：
  sequence = (4095 + 1) & 4095 = 0  ← 溢出！
  → 调用 nextTime() 等待下一毫秒
  → 返回 ID4096
```

### 4.3 场景 3：时钟回拨 1ms

```
当前时间：1700000000000
lastTimestamp：1700000001000

调用 generate()：
  currentTimestamp = 1700000000000 < lastTimestamp
  → 抛出 ClockBackwardsException

ClockSyncSnowflakeIdGenerator 捕获：
  1. log.warn(...)
  2. syncUninterruptibly(1700000001000)
     backwards = 1700000001000 - 1700000000000 = 1ms
     backwards <= spinThreshold (1ms)
     → while (backwards > 0) Thread.onSpinWait()
     → 等待直到 currentTime >= lastTimestamp
  3. 重试 generate()
  → 返回 ID
```

### 4.4 场景 4：时钟回拨 100ms

```
backwards = 100ms
spinThreshold < 100ms ≤ brokenThreshold (500ms)

→ TimeUnit.MILLISECONDS.sleep(100)
→ 等待 100ms
→ 重试 generate()
→ 返回 ID
```

### 4.5 场景 5：时钟回拨 > 500ms

```
backwards = 1000ms
backwards > brokenThreshold (500ms)

→ throw ClockTooManyBackwardsException
→ 业务需要人工干预
```

### 4.6 场景 6：sequenceResetThreshold 优化

```
配置：sequenceResetThreshold = 2047

调用 generate() 4096 次：
  第 1-2047 次：sequence 正常递增
  第 2048 次：sequence >= 2047 且时间前进
            → sequence = 0  ← 提前重置！
  第 2049-4096 次：sequence 继续递增

目的：避免 sequence 一直递增导致分片不均
```

---

## 五、关键设计决策

### 5.1 为什么用 synchronized？

- Snowflake ID 生成是 CPU 密集型操作
- 锁竞争时间极短 (纳秒级)
- 比 CAS 或 ReadWriteLock 更简单高效

### 5.2 为什么用 busy spin？

- 时钟回拨通常很短 (1-10ms)
- 线程切换开销 (10-100μs) 比忙等更大
- `Thread.onSpinWait()` 提示 CPU 优化 (x86 PAUSE 指令)

### 5.3 为什么需要 sequenceResetThreshold？

- 避免 sequence 一直递增导致分片不均
- 例如：当 sequence >= 2047 且时间前进时，提前重置为 0
- 这样可以更均匀地分布 ID

### 5.4 为什么 epoch 默认是 2021-01-01？

- 41 位时间戳可以用约 69 年
- 从 2021 年开始，可以用到 2090 年
- 避免使用 1970 年导致时间戳过大

---

## 六、配置参数详解

### 6.1 epoch (起始时间戳)

```java
private Instant epoch = Instant.ofEpochMilli(CosId.COSID_EPOCH);  // 2021-01-01T00:00:00Z
```

**作用**：ID 中的时间戳是相对于 epoch 的偏移量

**计算**：`diffTimestamp = currentTimestamp - epoch`

### 6.2 timestampBit (时间戳位数)

```java
private int timestampBit = 41;  // 默认 41 位
```

**容量**：2^41 = 2,199,023,255,552 毫秒 ≈ 69 年

### 6.3 machineBit (机器 ID 位数)

```java
private int machineBit = 10;  // 默认 10 位
```

**容量**：2^10 = 1024 台机器

### 6.4 sequenceBit (序列号位数)

```java
private int sequenceBit = 12;  // 默认 12 位
```

**容量**：2^12 = 4096 个 ID/毫秒

### 6.5 sequenceResetThreshold (序列号重置阈值)

```java
private long sequenceResetThreshold = ~(-1L << (sequenceBit - 1));  // 默认 2047
```

**作用**：当 sequence >= threshold 且时间前进时，提前重置为 0

---

## 七、与 CosId 的对比

### 7.1 目标项目

```java
// SnowflakeIdGenerator.java
public synchronized long generate() {
    long currentTimestamp = getCurrentTime();
    if (currentTimestamp < lastTimestamp) {
        throw new ClockBackwardsException(lastTimestamp, currentTimestamp);
    }
    if (currentTimestamp > lastTimestamp && sequence >= sequenceResetThreshold) {
        sequence = 0L;
    }
    sequence = (sequence + 1) & maxSequence;
    if (sequence == 0L) {
        currentTimestamp = nextTime();
    }
    lastTimestamp = currentTimestamp;
    long diffTimestamp = currentTimestamp - epoch;
    if (diffTimestamp > maxTimestamp) {
        throw new TimestampOverflowException(epoch, diffTimestamp, maxTimestamp);
    }
    return diffTimestamp << timestampLeft | machineId << machineLeft | sequence;
}
```

### 7.2 CosId

```java
// AbstractSnowflakeId.java
@Override
public synchronized long generate() {
    long currentTimestamp = getCurrentTime();
    if (currentTimestamp < lastTimestamp) {
        throw new ClockBackwardsException(lastTimestamp, currentTimestamp);
    }
    if (currentTimestamp > lastTimestamp && sequence >= sequenceResetThreshold) {
        sequence = 0L;
    }
    sequence = (sequence + 1) & maxSequence;
    if (sequence == 0L) {
        currentTimestamp = nextTime();
    }
    lastTimestamp = currentTimestamp;
    long diffTimestamp = (currentTimestamp - epoch);
    if (diffTimestamp > maxTimestamp) {
        throw new TimestampOverflowException(epoch, diffTimestamp, maxTimestamp);
    }
    return diffTimestamp << timestampLeft | machineId << machineLeft | sequence;
}
```

### 7.3 对比结论

**完全等价**。核心逻辑、参数、异常处理完全一致，只是包名和注释不同。

---

## 八、总结

Snowflake ID 生成器的核心要点：

1. **位拼接**：时间戳 + 机器 ID + 序列号
2. **时钟回拨处理**：spin/sleep/抛异常 三级策略
3. **序列号溢出**：等待下一毫秒
4. **线程安全**：synchronized 保证
5. **高性能**：本地生成，无网络调用

通过 ClockSync 装饰器，实现了自动时钟同步，提高了可用性。
