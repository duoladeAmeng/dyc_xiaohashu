# Segment ID 生成器详解

## 一、整体思路

### 1.1 什么是 Segment 算法

Segment 算法是**号段模式**，核心思想是：

**每次从数据库批量获取一段 ID，本地分配，减少数据库访问**

```
数据库 (last_max_id)
    │
    ├── 批量获取 (step=10000)
    │   └── last_max_id += 10000
    │       └── 返回 [0, 10000]
    │
    └── 本地分配
        ├── ID 0 → 线程 A
        ├── ID 1 → 线程 B
        ├── ...
        └── ID 9999 → 线程 Z
```

### 1.2 核心设计目标

1. **高吞吐**：批量获取，减少数据库访问
2. **低延迟**：本地分配，无网络调用
3. **高可用**：双 Buffer 机制，无缝切换
4. **有序性**：号段内有序递增

### 1.3 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SegmentIdGenerator                                 │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  generate()                                                           │  │
│  │                                                                       │  │
│  │  1. [step == 1 特殊路径]                                              │  │
│  │     └── 直接调用 nextMaxId() 获取                                     │  │
│  │                                                                       │  │
│  │  2. [step > 1 常规路径]                                               │  │
│  │     ├── Fast Path (无锁)                                              │  │
│  │     │   └── segment.isAvailable() → incrementAndGet() → 返回          │  │
│  │     │                                                                 │  │
│  │     └── Slow Path (synchronized)                                      │  │
│  │         └── while(true)                                               │  │
│  │             ├── segment.isAvailable() → 返回                          │  │
│  │             └── 获取新 segment → segment = nextSegment                │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  IdSegment (号段)                                                      │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │  maxId: long          -- 号段最大值                             │  │  │
│  │  │  sequence: AtomicLong -- 当前序列号                             │  │  │
│  │  │  value: long          -- 当前值 (= sequence.get())              │  │  │
│  │  │  nextIdSegment: IdSegment  -- 下一个号段                        │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                       │  │
│  │  方法:                                                                │  │
│  │  - isAvailable(): value < maxId                                       │  │
│  │  - incrementAndGet(): sequence.incrementAndGet()                      │  │
│  │  - isOverflow(nextSeq): nextSeq > maxId                               │  │
│  │  - ensureSetNext(): 设置下一个号段                                    │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  SegmentAllocator (号段分配器)                                        │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │  nextMaxId(step): 从数据库获取新的最大 ID                       │  │  │
│  │  │  nextIdSegment(ttl): 创建新的号段                                │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  JdbcSegmentAllocator (JDBC 实现)                                     │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │  SQL:                                                            │  │  │
│  │  │  UPDATE cosid SET last_max_id=last_max_id+? WHERE name=?        │  │  │
│  │  │  SELECT last_max_id FROM cosid WHERE name=?                     │  │  │
│  │  │                                                                   │  │  │
│  │  │  流程:                                                            │  │  │
│  │  │  1. connection.setAutoCommit(false)                              │  │  │
│  │  │  2. UPDATE (获取行锁)                                            │  │  │
│  │  │  3. SELECT (在同事务内)                                          │  │  │
│  │  │  4. connection.commit()                                          │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、具体实现

### 2.1 IdSegment 接口

**文件位置**：`core/segment/IdSegment.java`

```java
public interface IdSegment {
    
    long TIME_TO_LIVE_FOREVER = Long.MAX_VALUE;
    
    // 获取号段最大值
    long getMaxId();
    
    // 获取当前值
    long getValue();
    
    // 号段是否可用
    boolean isAvailable();
    
    // 自增并返回
    long incrementAndGet();
    
    // 检查是否溢出
    boolean isOverflow(long nextSeq);
    
    // 获取下一个号段
    IdSegment getNextIdSegment();
    
    // 确保设置下一个号段
    void ensureNextIdSegment(IdSegment nextIdSegment);
}
```

### 2.2 DefaultIdSegment 类

**文件位置**：`core/segment/DefaultIdSegment.java`

#### 2.2.1 核心字段

```java
public class DefaultIdSegment implements IdSegment {
    
    // 溢出号段 (用于初始化)
    public static final IdSegment OVERFLOW = new DefaultIdSegment(0, 0, 0);
    
    private final long maxId;              // 号段最大值
    private final AtomicLong sequence;     // 当前序列号
    private volatile long value;           // 当前值
    private volatile IdSegment nextIdSegment;  // 下一个号段
}
```

#### 2.2.2 构造函数

```java
public DefaultIdSegment(long maxId, long sequence, long ttl) {
    this.maxId = maxId;
    this.sequence = new AtomicLong(sequence);
    this.value = sequence;
    this.nextIdSegment = null;
}
```

#### 2.2.3 isAvailable() 方法

```java
@Override
public boolean isAvailable() {
    return value < maxId;
}
```

**解释**：当 value < maxId 时，号段还有可用 ID

#### 2.2.4 incrementAndGet() 方法

```java
@Override
public long incrementAndGet() {
    return value = sequence.incrementAndGet();
}
```

**解释**：原子自增，保证线程安全

#### 2.2.5 isOverflow() 方法

```java
@Override
public boolean isOverflow(long nextSeq) {
    return nextSeq > maxId;
}
```

**解释**：当 nextSeq > maxId 时，号段已用完

#### 2.2.6 ensureNextIdSegment() 方法

```java
@Override
public void ensureNextIdSegment(IdSegment nextIdSegment) {
    if (this.nextIdSegment != null) {
        return;  // 已设置，直接返回
    }
    this.nextIdSegment = nextIdSegment;
}
```

**解释**：确保只设置一次下一个号段

### 2.3 MergedIdSegment 类

**文件位置**：`core/segment/MergedIdSegment.java`

#### 2.3.1 设计目的

将多个小号段合并成一个大号段，减少号段切换频率。

#### 2.3.2 实现

```java
public class MergedIdSegment implements IdSegment {
    
    private final IdSegment firstSegment;    // 第一个号段
    private final IdSegment secondSegment;   // 第二个号段
    
    @Override
    public long getMaxId() {
        return secondSegment.getMaxId();  // 返回第二个号段的最大值
    }
    
    @Override
    public boolean isAvailable() {
        return firstSegment.isAvailable() || secondSegment.isAvailable();
    }
    
    @Override
    public long incrementAndGet() {
        if (firstSegment.isAvailable()) {
            return firstSegment.incrementAndGet();
        }
        return secondSegment.incrementAndGet();
    }
    
    @Override
    public boolean isOverflow(long nextSeq) {
        return secondSegment.isOverflow(nextSeq);
    }
}
```

### 2.4 SegmentAllocator 接口

**文件位置**：`core/segment/SegmentAllocator.java`

```java
public interface SegmentAllocator {
    
    // 获取命名空间
    String getNamespace();
    
    // 获取号段名称
    String getName();
    
    // 获取步长
    long getStep();
    
    // 获取命名空间:名称
    default String getNamespacedName() {
        return getNamespace() + "." + getName();
    }
    
    // 获取下一个最大 ID
    long nextMaxId(long step);
    
    // 获取下一个号段
    default IdSegment nextIdSegment(long ttl) {
        long maxId = nextMaxId(getStep());
        return new DefaultIdSegment(maxId, maxId - getStep(), ttl);
    }
    
    // 是否允许重置
    default boolean allowReset() {
        return false;
    }
    
    // 获取下一个号段链
    default IdSegmentChain nextIdSegmentChain(IdSegmentChain previousChain, int segments, long ttl) {
        // 默认实现：获取 segments 个号段
        long maxId = nextMaxId(getStep() * segments);
        IdSegmentChain chain = previousChain;
        for (int i = 0; i < segments; i++) {
            IdSegment segment = new DefaultIdSegment(maxId, maxId - getStep(), ttl);
            chain = chain.ensureSetNext(preChain -> segment).getNext();
        }
        return chain;
    }
    
    // 校验 step
    static void ensureStep(long step) {
        if (step <= 0) {
            throw new IllegalArgumentException("step:[%s] must be greater than 0!");
        }
    }
}
```

### 2.5 JdbcSegmentAllocator 类

**文件位置**：`jdbc/JdbcSegmentAllocator.java`

#### 2.5.1 SQL 语句

```java
// 更新最大 ID
public static final String INCREMENT_MAX_ID_SQL = 
    "update cosid set last_max_id=(last_max_id + ?),last_fetch_time=unix_timestamp() where name = ?;";

// 获取最大 ID
public static final String FETCH_MAX_ID_SQL = 
    "select last_max_id from cosid where name = ?;";
```

#### 2.5.2 nextMaxId() 方法

```java
@Override
public long nextMaxId(long step) {
    SegmentAllocator.ensureStep(step);
    
    try (Connection connection = dataSource.getConnection()) {
        // 1. 开启事务
        connection.setAutoCommit(false);
        
        // 2. UPDATE (获取行锁)
        incrementMaxId(connection, step);
        
        // 3. SELECT (在同事务内)
        long maxId = fetchMaxId(connection);
        
        // 4. 提交事务
        connection.commit();
        
        return maxId;
    } catch (SQLException sqlException) {
        throw new IdGeneratorException(sqlException);
    }
}
```

**关键点**：
- **同一事务**：UPDATE 和 SELECT 在同一事务内
- **行锁**：UPDATE 获取行锁，防止并发问题
- **原子性**：UPDATE + SELECT 原子执行

#### 2.5.3 incrementMaxId() 方法

```java
private void incrementMaxId(Connection connection, long step) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INCREMENT_MAX_ID_SQL)) {
        statement.setLong(1, step);
        statement.setString(2, getNamespacedName());
        int affected = statement.executeUpdate();
        if (affected == 0) {
            throw new SegmentNameMissingException(namespace, name);
        }
    }
}
```

#### 2.5.4 fetchMaxId() 方法

```java
private long fetchMaxId(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(FETCH_MAX_ID_SQL)) {
        statement.setString(1, getNamespacedName());
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new NotFoundMaxIdException(getNamespacedName());
            }
            return resultSet.getLong(1);
        }
    }
}
```

### 2.6 SegmentIdGenerator 类

**文件位置**：`core/segment/SegmentIdGenerator.java`

#### 2.6.1 核心字段

```java
public class SegmentIdGenerator implements IdGenerator {
    
    public static final int ONE_STEP = 1;  // 单步长特殊值
    
    private final long idSegmentTtl;           // 号段有效期
    private final SegmentAllocator segmentAllocator;  // 号段分配器
    private volatile IdSegment segment = DefaultIdSegment.OVERFLOW;  // 当前号段
}
```

#### 2.6.2 generate() 方法详解

```java
@Override
public long generate() {
    // 1. step == 1 特殊路径
    if (segmentAllocator.getStep() == ONE_STEP) {
        GroupedAccessor.setIfNotNever(segmentAllocator.group());
        return segmentAllocator.nextMaxId();
    }
    
    // 2. Fast Path (无锁)
    long nextSeq;
    if (segment.isAvailable()) {
        nextSeq = segment.incrementAndGet();
        if (!segment.isOverflow(nextSeq)) {
            return nextSeq;
        }
    }

    // 3. Slow Path (synchronized)
    synchronized (this) {
        while (true) {
            // 3.1 再次检查
            if (segment.isAvailable()) {
                nextSeq = segment.incrementAndGet();
                if (!segment.isOverflow(nextSeq)) {
                    return nextSeq;
                }
            }
            
            // 3.2 获取新号段
            IdSegment nextSegment = segmentAllocator.nextIdSegment(idSegmentTtl);
            if (!segmentAllocator.allowReset()) {
                segment.ensureNextIdSegment(nextSegment);
            }
            segment = nextSegment;
        }
    }
}
```

**流程图**：
```
调用 generate()
    │
    ├── [step == 1]
    │   └── 直接调用 nextMaxId()
    │
    └── [step > 1]
        │
        ├── Fast Path (无锁)
        │   └── segment.isAvailable()
        │       ├── 是 → incrementAndGet()
        │       │   ├── 未溢出 → 返回
        │       │   └── 溢出 → 继续
        │       └── 否 → 继续
        │
        └── Slow Path (synchronized)
            └── while(true)
                ├── segment.isAvailable()
                │   ├── 是 → incrementAndGet()
                │   │   ├── 未溢出 → 返回
                │   │   └── 溢出 → 继续
                │   └── 否 → 继续
                └── 获取新号段
                    └── segment = nextSegment
```

---

## 三、数据库设计

### 3.1 cosid 表

```sql
CREATE TABLE cosid (
    name VARCHAR(128) NOT NULL,           -- 号段名称 (主键)
    namespace VARCHAR(128) NOT NULL,      -- 命名空间
    last_max_id BIGINT NOT NULL,          -- 最大 ID
    last_fetch_time BIGINT NOT NULL,      -- 最后获取时间
    PRIMARY KEY (namespace, name)
);
```

### 3.2 示例数据

```sql
INSERT INTO cosid (name, namespace, last_max_id, last_fetch_time) 
VALUES ('segment', 'xiaohashu', 10000, UNIX_TIMESTAMP());
```

---

## 四、场景推演

### 4.1 场景 1：正常获取

```
初始状态：
  last_max_id = 0
  step = 10000

调用 nextMaxId(10000)：
  1. UPDATE SET last_max_id = last_max_id + 10000
     └── last_max_id = 10000
  2. SELECT last_max_id
     └── 返回 10000
  3. 创建号段: [0, 10000]

调用 generate()：
  segment = DefaultIdSegment(maxId=10000, sequence=0)
  
  第 1 次：incrementAndGet() → 1
  第 2 次：incrementAndGet() → 2
  ...
  第 10000 次：incrementAndGet() → 10000
  第 10001 次：isOverflow(10001) → true → 获取新号段
```

### 4.2 场景 2：并发获取

```
线程 A 和 B 同时调用 nextMaxId(10000)：

线程 A：
  1. UPDATE SET last_max_id = last_max_id + 10000
     └── 获取行锁，last_max_id = 10000
  2. SELECT last_max_id → 10000
  3. COMMIT
  4. 返回号段 [0, 10000]

线程 B：
  1. UPDATE SET last_max_id = last_max_id + 10000
     └── 等待行锁...
  2. 获取行锁，last_max_id = 20000
  3. SELECT last_max_id → 20000
  4. COMMIT
  5. 返回号段 [10000, 20000]

结果：两个线程获取的号段不重叠
```

### 4.3 场景 3：号段切换

```
线程 A 正在使用号段 [0, 10000]：
  - 第 9999 次调用：incrementAndGet() → 9999
  - 第 10000 次调用：incrementAndGet() → 10000
  - 第 10001 次调用：isOverflow(10001) → true

线程 A 进入 synchronized：
  1. 再次检查 segment.isAvailable() → false
  2. 获取新号段：nextIdSegment(10000)
     └── nextMaxId(10000) → 20000
     └── 创建号段 [10000, 20000]
  3. segment = nextSegment
  4. 继续循环
  5. incrementAndGet() → 10001
  6. 返回
```

### 4.4 场景 4：step == 1 特殊路径

```
step = 1 时：
  - 每次调用都直接获取一个 ID
  - 不使用本地缓存
  - 每次都访问数据库

优点：
  - 简单可靠
  - 不会有号段浪费

缺点：
  - 性能较低
  - 数据库压力大
```

---

## 五、关键设计决策

### 5.1 为什么需要 Fast Path？

- 大多数情况下，号段还有可用 ID
- Fast Path 无锁，性能更高
- 只有号段耗尽时才进入 Slow Path

### 5.2 为什么需要 synchronized？

- 号段切换时需要原子操作
- 防止多个线程同时获取新号段
- 保证号段切换的正确性

### 5.3 为什么需要 ensureNextIdSegment？

- 防止多个线程同时设置下一个号段
- 确保只设置一次
- 保证链表结构的正确性

### 5.4 为什么需要 MergedIdSegment？

- 将多个小号段合并成一个大号段
- 减少号段切换频率
- 提高性能

---

## 六、与 CosId 的对比

### 6.1 SQL 语句

**完全一致**。所有 SQL 语句与 CosId 相同。

### 6.2 nextMaxId() 流程

**完全一致**。都是：UPDATE → SELECT → COMMIT

### 6.3 generate() 逻辑

**完全一致**。都是：Fast Path → Slow Path

### 6.4 号段结构

**完全一致**。都是：maxId + AtomicLong sequence

---

## 七、总结

Segment ID 生成器的核心要点：

1. **号段模式**：批量获取，减少数据库访问
2. **Fast Path**：大多数情况下无锁
3. **Synchronized**：号段切换时加锁
4. **AtomicLong**：保证序列号自增的线程安全
5. **同一事务**：UPDATE 和 SELECT 在同一事务内

通过这些机制，实现了高吞吐、低延迟的 ID 生成。
