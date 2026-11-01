# SegmentChain ID 生成器详解

## 一、整体思路

### 1.1 什么是 SegmentChain

SegmentChain 是**链式号段模式**，在 Segment 基础上增加了：

1. **链式结构**：多个号段组成链表
2. **异步预取**：后台线程提前获取号段
3. **动态调整**：根据负载调整预取距离
4. **饥饿检测**：自动扩大预取距离

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SegmentChain 结构                                  │
│                                                                             │
│  headChain ──────────────────────────────────────────────► tailChain         │
│      │                                                       │              │
│      ▼                                                       ▼              │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐                   │
│  │Segment 0│───►│Segment 1│───►│Segment 2│───►│Segment 3│                   │
│  │[0,10000]│    │[10000,  │    │[20000,  │    │[30000,  │                   │
│  │         │    │ 20000]  │    │ 30000]  │    │ 40000]  │                   │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘                   │
│       ▲                                                       │              │
│       │                                                       │              │
│   当前使用                                               后台预取               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 核心设计目标

1. **超高吞吐**：链式结构减少锁竞争
2. **低延迟**：异步预取，提前获取号段
3. **高可用**：多个号段提供容错
4. **动态调整**：根据负载自动调整预取距离

### 1.3 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       SegmentChainIdGenerator                               │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  generate()                                                           │  │
│  │                                                                       │  │
│  │  1. 遍历链 (headChain → next → ...)                                   │  │
│  │     └── 找到可用节点 → incrementAndGet() → 返回                       │  │
│  │                                                                       │  │
│  │  2. 链耗尽，尝试扩展                                                   │  │
│  │     └── headChain.trySetNext(preChain -> generateNext(...))           │  │
│  │         └── 成功 → forward() → 返回                                   │  │
│  │                                                                       │  │
│  │  3. 发送饥饿信号                                                       │  │
│  │     └── prefetchJob.hungry()                                          │  │
│  │                                                                       │  │
│  │  4. 重复循环                                                           │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  PrefetchJob (后台预取任务)                                           │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │  prefetch()                                                     │  │  │
│  │  │                                                                   │  │  │
│  │  │  1. 判断饥饿                                                     │  │  │
│  │  │     └── wakeupTimeGap < hungerThreshold (5s)                    │  │  │
│  │  │                                                                   │  │  │
│  │  │  2. 动态调整 prefetchDistance                                    │  │  │
│  │  │     ├── 饥饿 → prefetchDistance *= 2                             │  │  │
│  │  │     └── 饱和 → prefetchDistance /= 2                             │  │  │
│  │  │                                                                   │  │  │
│  │  │  3. 计算 safeGap                                                 │  │  │
│  │  │     └── safeGap = safeDistance - headToTailGap                   │  │  │
│  │  │                                                                   │  │  │
│  │  │  4. 预取号段                                                     │  │  │
│  │  │     └── appendChain(availableHeadChain, prefetchSegments)        │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  IdSegmentChain (链节点)                                              │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │  idSegment: IdSegment      -- 实际号段                          │  │  │
│  │  │  version: long             -- 版本号                            │  │  │
│  │  │  next: IdSegmentChain      -- 下一个节点                        │  │  │
│  │  │                                                                   │  │  │
│  │  │  方法:                                                            │  │  │
│  │  │  - isAvailable(): 号段是否可用                                   │  │  │
│  │  │  - incrementAndGet(): 自增并返回                                 │  │  │
│  │  │  - isOverflow(nextSeq): 是否溢出                                 │  │  │
│  │  │  - trySetNext(): 尝试设置下一个节点                              │  │  │
│  │  │  - ensureSetNext(): 确保设置下一个节点                           │  │  │
│  │  │  - gap(): 计算与另一个节点的差距                                 │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、具体实现

### 2.1 IdSegmentChain 类

**文件位置**：`core/segment/IdSegmentChain.java`

#### 2.1.1 核心字段

```java
public class IdSegmentChain implements IdSegment, Comparable<IdSegmentChain> {
    
    // 版本号生成器
    private static final AtomicLong VERSION = new AtomicLong(0);
    
    // 根节点
    private static final IdSegmentChain ROOT = new IdSegmentChain(null, null);
    
    private final IdSegment idSegment;        // 实际号段
    private final long version;               // 版本号
    private volatile IdSegmentChain next;     // 下一个节点
}
```

#### 2.1.2 构造函数

```java
private IdSegmentChain(IdSegment idSegment, IdSegmentChain previousChain) {
    this.idSegment = idSegment;
    this.version = VERSION.incrementAndGet();
    this.next = null;
}
```

#### 2.1.3 newRoot() 方法

```java
public static IdSegmentChain newRoot(boolean allowReset) {
    if (allowReset) {
        return ROOT;
    }
    return new IdSegmentChain(null, null);
}
```

#### 2.1.4 isAvailable() 方法

```java
@Override
public boolean isAvailable() {
    return idSegment != null && idSegment.isAvailable();
}
```

#### 2.1.5 incrementAndGet() 方法

```java
@Override
public long incrementAndGet() {
    return idSegment.incrementAndGet();
}
```

#### 2.1.6 isOverflow() 方法

```java
@Override
public boolean isOverflow(long nextSeq) {
    return idSegment.isOverflow(nextSeq);
}
```

#### 2.1.7 trySetNext() 方法

```java
public boolean trySetNext(Function<IdSegmentChain, IdSegmentChain> generator) {
    if (next != null) {
        return false;  // 已设置，返回 false
    }
    IdSegmentChain newChain = generator.apply(this);
    synchronized (this) {
        if (next != null) {
            return false;  // 双重检查
        }
        next = newChain;
    }
    return true;
}
```

**关键点**：
- **双重检查**：先检查 next == null，再在 synchronized 中检查
- **CAS 语义**：保证只设置一次

#### 2.1.8 ensureSetNext() 方法

```java
public IdSegmentChain ensureSetNext(Function<IdSegmentChain, IdSegmentChain> generator) {
    if (next != null) {
        return next;  // 已设置，直接返回
    }
    IdSegmentChain newChain = generator.apply(this);
    synchronized (this) {
        if (next != null) {
            return next;  // 双重检查
        }
        next = newChain;
    }
    return next;
}
```

#### 2.1.9 gap() 方法

```java
public int gap(IdSegmentChain other, long step) {
    if (step == 0) {
        return 0;
    }
    return (int) ((other.getVersion() - this.getVersion()) * step);
}
```

**解释**：计算两个节点之间的 ID 数量

#### 2.1.10 compareTo() 方法

```java
@Override
public int compareTo(IdSegmentChain other) {
    return Long.compare(this.version, other.version);
}
```

### 2.2 SegmentChainIdGenerator 类

**文件位置**：`core/segment/SegmentChainIdGenerator.java`

#### 2.2.1 核心字段

```java
public class SegmentChainIdGenerator implements IdGenerator {
    
    public static final int DEFAULT_SAFE_DISTANCE = 2;  // 默认安全距离
    
    private final long idSegmentTtl;                    // 号段有效期
    private final int safeDistance;                     // 安全距离
    private final SegmentAllocator segmentAllocator;   // 号段分配器
    private final PrefetchJob prefetchJob;             // 预取任务
    private volatile IdSegmentChain headChain;         // 头节点
}
```

#### 2.2.2 构造函数

```java
public SegmentChainIdGenerator(long idSegmentTtl, int safeDistance, SegmentAllocator segmentAllocator,
                               PrefetchWorkerExecutorService prefetchWorkerExecutorService) {
    // 1. 参数校验
    if (idSegmentTtl <= 0) {
        throw new IllegalArgumentException(String.format("Illegal idSegmentTtl parameter:[%s].", idSegmentTtl));
    }
    if (safeDistance <= 0) {
        throw new IllegalArgumentException("The safety distance must be greater than 0.");
    }
    
    // 2. 初始化
    this.headChain = IdSegmentChain.newRoot(segmentAllocator.allowReset());
    this.idSegmentTtl = idSegmentTtl;
    this.safeDistance = safeDistance;
    this.segmentAllocator = segmentAllocator;
    
    // 3. 创建并提交预取任务
    this.prefetchJob = new PrefetchJob(headChain);
    prefetchWorkerExecutorService.submit(prefetchJob);
}
```

#### 2.2.3 generate() 方法详解

```java
@Override
public long generate() {
    while (true) {
        // 1. 遍历链
        IdSegmentChain currentChain = headChain;
        while (currentChain != null) {
            if (currentChain.isAvailable()) {
                long nextSeq = currentChain.incrementAndGet();
                if (!currentChain.isOverflow(nextSeq)) {
                    forward(currentChain);  // 前进头节点
                    return nextSeq;
                }
            }
            currentChain = currentChain.getNext();
        }

        // 2. 链耗尽，尝试扩展
        try {
            IdSegmentChain preHead = headChain;
            if (preHead.trySetNext(preChain -> generateNext(preChain, safeDistance))) {
                IdSegmentChain nextChain = preHead.getNext();
                forward(nextChain);
                log.debug("Generate [{}] - headChain.version:[{}->{}].", 
                    segmentAllocator.getNamespacedName(), preHead.getVersion(), nextChain.getVersion());
            }
        } catch (NextIdSegmentExpiredException exception) {
            log.warn("Generate [{}] - gave up this next IdSegmentChain.", 
                segmentAllocator.getNamespacedName(), exception);
        }
        
        // 3. 发送饥饿信号
        this.prefetchJob.hungry();
    }
}
```

**流程图**：
```
调用 generate()
    │
    ├── 1. 遍历链
    │   └── headChain → next → ...
    │       ├── 找到可用节点
    │       │   └── incrementAndGet()
    │       │       ├── 未溢出 → forward() → 返回
    │       │       └── 溢出 → 继续
    │       └── 未找到 → 继续
    │
    ├── 2. 链耗尽
    │   └── headChain.trySetNext(...)
    │       ├── 成功 → forward() → 返回
    │       └── 失败 → 继续
    │
    ├── 3. 发送饥饿信号
    │   └── prefetchJob.hungry()
    │
    └── 4. 重复循环
```

#### 2.2.4 forward() 方法

```java
private void forward(IdSegmentChain forwardChain) {
    // 1. 检查版本号
    if (headChain.getVersion() >= forwardChain.getVersion()) {
        return;  // 已经前进过了
    }
    
    log.debug("Forward [{}] - [{}] -> [{}].", 
        segmentAllocator.getNamespacedName(), headChain, forwardChain);
    
    // 2. 更新头节点
    if (segmentAllocator.allowReset()) {
        headChain = forwardChain;
    } else if (forwardChain.compareTo(headChain) > 0) {
        headChain = forwardChain;
    }
}
```

**关键点**：
- **版本号比较**：防止重复前进
- **allowReset**：决定是否重置头节点

#### 2.2.5 generateNext() 方法

```java
private IdSegmentChain generateNext(IdSegmentChain previousChain, int segments) {
    return segmentAllocator.nextIdSegmentChain(previousChain, segments, idSegmentTtl);
}
```

### 2.3 PrefetchJob 类

**文件位置**：`core/segment/SegmentChainIdGenerator.java` (内部类)

#### 2.3.1 核心字段

```java
public class PrefetchJob implements AffinityJob {
    
    private static final int MAX_PREFETCH_DISTANCE = 100_000_000;  // 最大预取距离
    private static final long hungerThreshold = 5;                 // 饥饿阈值 (秒)
    
    private volatile PrefetchWorker prefetchWorker;  // 预取工作线程
    private int prefetchDistance;                    // 当前预取距离
    private IdSegmentChain tailChain;               // 尾节点
    private volatile long lastHungerTime;           // 上次饥饿时间
}
```

#### 2.3.2 prefetch() 方法详解

```java
public void prefetch() {
    // 1. 判断饥饿
    long wakeupTimeGap = Clock.SYSTEM.secondTime() - lastHungerTime;
    boolean hunger = wakeupTimeGap < hungerThreshold;

    // 2. 动态调整 prefetchDistance
    int prePrefetchDistance = this.prefetchDistance;
    if (hunger) {
        // 饥饿：翻倍 (最大 100_000_000)
        this.prefetchDistance = Math.min(Math.multiplyExact(this.prefetchDistance, 2), MAX_PREFETCH_DISTANCE);
        log.info("Prefetch [{}] - Hunger, Safety distance expansion.[{}->{}]", 
            segmentAllocator.getNamespacedName(), prePrefetchDistance, this.prefetchDistance);
    } else {
        // 饱和：减半 (最小 safeDistance)
        this.prefetchDistance = Math.max(Math.floorDiv(this.prefetchDistance, 2), safeDistance);
        if (prePrefetchDistance > this.prefetchDistance) {
            log.info("Prefetch [{}] - Full, Safety distance shrinks.[{}->{}]", 
                segmentAllocator.getNamespacedName(), prePrefetchDistance, this.prefetchDistance);
        }
    }

    // 3. 找到可用的头节点
    IdSegmentChain availableHeadChain = SegmentChainIdGenerator.this.headChain;
    while (!availableHeadChain.getIdSegment().isAvailable()) {
        availableHeadChain = availableHeadChain.getNext();
        if (availableHeadChain == null) {
            availableHeadChain = tailChain;
            break;
        }
    }
    forward(availableHeadChain);

    // 4. 计算 safeGap
    int headToTailGap = availableHeadChain.gap(tailChain, segmentAllocator.getStep());
    int safeGap = safeDistance - headToTailGap;

    // 5. 决定预取数量
    if (safeGap <= 0 && !hunger) {
        log.trace("Prefetch [{}] - safeGap is less than or equal to 0, and is not hungry.", 
            segmentAllocator.getNamespacedName());
        return;
    }
    int prefetchSegments = hunger ? this.prefetchDistance : safeGap;

    // 6. 预取号段
    appendChain(availableHeadChain, prefetchSegments);
}
```

**流程图**：
```
调用 prefetch()
    │
    ├── 1. 判断饥饿
    │   └── wakeupTimeGap < 5s → hunger = true
    │
    ├── 2. 动态调整 prefetchDistance
    │   ├── 饥饿 → prefetchDistance *= 2 (最大 100_000_000)
    │   └── 饱和 → prefetchDistance /= 2 (最小 safeDistance)
    │
    ├── 3. 找到可用的头节点
    │   └── headChain → next → ...
    │       └── 找到第一个可用的
    │
    ├── 4. 计算 safeGap
    │   └── safeGap = safeDistance - headToTailGap
    │
    ├── 5. 决定预取数量
    │   ├── safeGap <= 0 且不饥饿 → 不预取
    │   ├── 饥饿 → prefetchDistance
    │   └── 饱和 → safeGap
    │
    └── 6. 预取号段
        └── appendChain(availableHeadChain, prefetchSegments)
```

#### 2.3.3 appendChain() 方法

```java
private void appendChain(IdSegmentChain availableHeadChain, int prefetchSegments) {
    log.debug("AppendChain [{}] - headChain.version:[{}] - tailChain.version:[{}] - prefetchSegments:[{}].", 
        segmentAllocator.getNamespacedName(), availableHeadChain.getVersion(), 
        tailChain.getVersion(), prefetchSegments);

    try {
        IdSegmentChain preTail = tailChain;
        
        // 1. 生成新链
        tailChain = tailChain.ensureSetNext(preChain -> generateNext(preChain, prefetchSegments)).getNext();
        
        // 2. 找到新的尾节点
        while (tailChain.getNext() != null) {
            tailChain = tailChain.getNext();
        }
        
        log.debug("AppendChain [{}] - restTail - tailChain.version:[{}:{}->{}] .", 
            segmentAllocator.getNamespacedName(),
            preTail.gap(tailChain, segmentAllocator.getStep()), 
            preTail.getVersion(), tailChain.getVersion());
    } catch (NextIdSegmentExpiredException exception) {
        log.warn("AppendChain [{}] - gave up this next IdSegmentChain.", 
            segmentAllocator.getNamespacedName(), exception);
    }
}
```

#### 2.3.4 hungry() 方法

```java
@Override
public void setHungerTime(long hungerTime) {
    lastHungerTime = hungerTime;
}

public void hungry() {
    lastHungerTime = Clock.SYSTEM.secondTime();
}
```

### 2.4 PrefetchWorkerExecutorService 类

**文件位置**：`core/segment/concurrent/PrefetchWorkerExecutorService.java`

#### 2.4.1 核心字段

```java
public class PrefetchWorkerExecutorService {
    
    public static final Duration DEFAULT_PREFETCH_PERIOD = Duration.ofSeconds(1);
    
    private final Duration prefetchPeriod;           // 预取周期
    private final ScheduledExecutorService executor;  // 调度器
    private final ConcurrentHashMap<String, AffinityJob> jobs;  // 任务映射
}
```

#### 2.4.2 submit() 方法

```java
public void submit(AffinityJob job) {
    jobs.put(job.getJobId(), job);
    // 定时执行 job.run()
    executor.scheduleWithFixedDelay(
        () -> job.run(),
        prefetchPeriod.toMillis(),
        prefetchPeriod.toMillis(),
        TimeUnit.MILLISECONDS
    );
}
```

---

## 三、饥饿检测机制

### 3.1 什么是饥饿

当 ID 生成速度超过预取速度时，链会耗尽，需要等待后台预取，这就是饥饿。

### 3.2 饥饿检测

```java
long wakeupTimeGap = Clock.SYSTEM.secondTime() - lastHungerTime;
boolean hunger = wakeupTimeGap < hungerThreshold;  // 5 秒
```

**解释**：
- 如果上次饥饿到现在不到 5 秒，说明还在饥饿
- 如果超过 5 秒，说明已经恢复

### 3.3 动态调整

```
饥饿时：
  prefetchDistance *= 2 (最大 100_000_000)

饱和时：
  prefetchDistance /= 2 (最小 safeDistance)
```

**目的**：
- 饥饿时：加大预取力度，快速恢复
- 饱和时：减小预取力度，节省资源

---

## 四、场景推演

### 4.1 场景 1：正常生成

```
初始状态：
  headChain → Segment[0, 10000] → Segment[10000, 20000] → null
  tailChain → Segment[10000, 20000]

调用 generate()：
  1. 遍历链
     └── headChain.isAvailable() → true
  2. incrementAndGet() → 1
  3. isOverflow(1) → false
  4. forward(headChain)
  5. 返回 1
```

### 4.2 场景 2：链耗尽

```
状态：
  headChain → Segment[0, 10000] (已用完)
  tailChain → Segment[10000, 20000]

调用 generate()：
  1. 遍历链
     └── headChain.isAvailable() → false
  2. headChain.getNext().isAvailable() → true
  3. incrementAndGet() → 10001
  4. isOverflow(10001) → false
  5. forward(headChain.getNext())
  6. 返回 10001
```

### 4.3 场景 3：完全耗尽

```
状态：
  headChain → Segment[0, 10000] (已用完)
  headChain.next → null

调用 generate()：
  1. 遍历链
     └── 未找到可用节点
  2. trySetNext(...)
     └── 生成新链：Segment[10000, 20000] → Segment[20000, 30000]
  3. forward(newChain)
  4. 返回 10001
  5. hungry() → lastHungerTime = currentTime
```

### 4.4 场景 4：后台预取

```
PrefetchJob 定时执行：
  1. wakeupTimeGap = 3s < 5s → hunger = true
  2. prefetchDistance *= 2
  3. safeGap = 2 - 0 = 2
  4. prefetchSegments = 2
  5. appendChain(...)
     └── 生成新链：Segment[30000, 40000] → Segment[40000, 50000]
  6. tailChain = Segment[40000, 50000]
```

---

## 五、关键设计决策

### 5.1 为什么需要链式结构？

- 减少锁竞争
- 多个线程可以同时使用不同节点
- 提高并发性能

### 5.2 为什么需要异步预取？

- 提前获取号段，避免等待
- 提高吞吐量
- 降低延迟

### 5.3 为什么需要动态调整？

- 适应不同负载
- 饥饿时加大预取
- 饱和时节省资源

### 5.4 为什么需要饥饿检测？

- 自动识别负载变化
- 触发动态调整
- 提高自适应能力

---

## 六、与 CosId 的对比

### 6.1 链式结构

**完全一致**。都是：IdSegmentChain 组成链表

### 6.2 预取机制

**完全一致**。都是：PrefetchJob 定时预取

### 6.3 动态调整

**完全一致**。都是：饥饿翻倍，饱和减半

### 6.4 饥饿检测

**完全一致**。都是：wakeupTimeGap < hungerThreshold

---

## 七、总结

SegmentChain ID 生成器的核心要点：

1. **链式结构**：多个号段组成链表，减少锁竞争
2. **异步预取**：后台线程提前获取号段，避免等待
3. **动态调整**：根据负载自动调整预取距离
4. **饥饿检测**：自动识别负载变化，触发动态调整
5. **版本号**：保证链表操作的正确性

通过这些机制，实现了超高吞吐、低延迟的 ID 生成，适合高并发场景。
