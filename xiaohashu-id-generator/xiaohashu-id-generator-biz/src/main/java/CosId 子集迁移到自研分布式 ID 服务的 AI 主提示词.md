你是一名资深 Java 架构师、分布式系统工程师和代码迁移专家。

我已经将开源项目 CosId 克隆到本地。你的任务不是简单复制 CosId，也不是支持它的全部功能，而是基于 CosId 的成熟设计和实现，迁移出一个适合我自己的“生产级分布式 ID 生成服务”的核心实现。

## 一、总体目标

基于本地 CosId 源码，提取、理解、重构并迁移以下 3 类能力：

1. SnowflakeId
2. SegmentId：数据库号段模式
3. SegmentChainId：号段 + 预取 + 无锁/低竞争设计

只保留 JDBC 作为分布式协调和持久化方式。

不需要支持：

- Redis
- ZooKeeper
- MongoDB
- Kubernetes StatefulSet MachineId Distributor
- Spring Redis
- 其他非 JDBC 的 Segment Distributor
- CosId 中与当前目标无关的兼容层、扩展模块、Proxy、Web、Demo、文档站点等功能

但是设计必须保留良好的扩展能力，使未来如果需要 Redis、其他数据库、不同 MachineIdAllocator、不同 SegmentAllocator 时，可以通过接口扩展，而不是修改核心算法。

最终结果必须是“生产可用代码”，不是 Demo，不是简单示例。

---

## 二、工作原则

在开始修改代码之前，必须先完整分析本地 CosId 中与目标有关的实现。

优先阅读并追踪以下代码关系：

- SnowflakeId
- MillisecondSnowflakeId
- ClockBackwardsSynchronizer
- MachineIdDistributor
- AbstractMachineIdDistributor
- JdbcMachineIdDistributor
- MachineState
- MachineStateStorage
- InstanceId
- SegmentId
- SegmentChainId
- IdSegment
- IdSegmentDistributor
- JdbcIdSegmentDistributor
- PrefetchWorker
- safeDistance 相关实现
- 号段切换逻辑
- 并发控制逻辑
- JDBC 表结构
- JDBC SQL
- 事务处理
- 乐观锁 / CAS / version 机制
- JMH benchmark 中涉及这些算法的测试
- CosId 对时钟回拨、machineId 回收、segment 饥饿、segment 预取的处理

不要只看接口和 README。

必须顺着调用链阅读真正的生产实现和测试代码。

---

## 三、先做代码分析，不要立刻重写

第一阶段先输出一份源码分析结果，至少包括：

### Snowflake

说明：

- ID bit layout
- epoch
- timestamp bit
- machine bit
- sequence bit
- 同一毫秒 sequence 用尽后的处理
- clock backwards 检测方式
- clock backwards 恢复方式
- machineId 分配方式
- JdbcMachineIdDistributor 的数据模型
- machineId 回收机制
- instanceId 的意义
- stable / unstable instance 的区别
- lastTimestamp 为什么需要保存
- safeGuardDuration 的意义
- 服务异常退出时 machineId 怎么处理
- 多实例同时启动时如何避免分配相同 machineId

### SegmentId

说明：

- 数据库中的号段记录是什么结构
- step 是什么
- maxId 是什么
- 每次从 DB 申请号段的 SQL
- 是否使用事务
- 是否使用 CAS / version
- 多实例同时申请号段如何保证唯一
- 数据库失败时已有号段能否继续使用
- 当前号段耗尽后的行为
- 单个业务 namespace/tag 如何隔离

### SegmentChainId

重点分析：

- 为什么比普通 SegmentId 快
- SegmentChain 的结构
- 当前 segment
- next segment
- prefetch 的触发条件
- safeDistance
- safeDistance 动态扩张与收缩
- hunger 状态
- PrefetchWorker 的线程模型
- 号段切换是否加锁
- CAS 的位置
- AtomicLong / VarHandle / volatile 的使用
- 并发场景下如何避免：
  - 重复 ID
  - 跳段导致浪费过多
  - 多线程重复 prefetch
  - 当前 segment 已耗尽但 next 尚未加载
  - DB 短暂不可用

分析完成后，先给出“建议保留的算法”和“建议删除的 CosId 框架代码”。

---

## 四、目标架构

不要照搬 CosId 的模块结构。

请设计一个更小、更清晰的自研模块。

推荐结构可以类似：

id-generator-core
id-generator-jdbc
id-generator-spring-boot-starter

其中：

### id-generator-core

只包含算法和抽象：

- IdGenerator
- SnowflakeIdGenerator
- SegmentIdGenerator
- SegmentChainIdGenerator

以及：

- MachineIdAllocator
- SegmentAllocator
- MachineState
- IdSegment
- ClockBackwardsHandler
- InstanceIdentity

核心模块禁止依赖：

- Spring
- JDBC
- Redis
- ZooKeeper

核心算法应该可以单元测试。

### id-generator-jdbc

实现：

- JdbcMachineIdAllocator
- JdbcSegmentAllocator
- JDBC Repository
- SQL
- transaction handling
- schema initializer 可选

数据库层必须与核心算法解耦。

### id-generator-spring-boot-starter

负责：

- ConfigurationProperties
- AutoConfiguration
- Bean 创建
- 生命周期管理
- graceful shutdown
- health indicator
- metrics integration

Spring 只是适配层。

---

## 五、只支持 JDBC，但接口必须可扩展

当前只实现 JDBC。

但是必须抽象成：

MachineIdAllocator

例如：

```java
public interface MachineIdAllocator {
    MachineState acquire(...);

    void heartbeat(...);

    void release(...);
}
```

未来可以新增：

RedisMachineIdAllocator
ZookeeperMachineIdAllocator

而不修改 SnowflakeIdGenerator。

Segment 同理：

```java
public interface SegmentAllocator {
    IdSegment nextSegment(String namespace, int step);
}
```

当前：

JdbcSegmentAllocator

未来允许：

RedisSegmentAllocator
RemoteSegmentAllocator

SegmentIdGenerator 和 SegmentChainIdGenerator 不允许直接写 JDBC 代码。

---

## 六、生产级 Snowflake 要求

Snowflake 实现至少满足：

### ID 唯一性

必须保证：

- 多 JVM 实例不重复
- JVM 重启不重复
- machineId 回收时不重复
- clock backwards 不重复
- sequence overflow 不重复

### machineId

只实现 JDBC allocator。

数据库中需要维护：

- namespace
- instance_id
- machine_id
- last_timestamp
- last_heartbeat
- status
- version 或其他并发控制字段

需要分析是否真的需要这些字段，不要机械照抄。

要求支持：

- acquire machineId
- heartbeat
- release
- timeout reclaim
- restart reuse if safe
- clock backwards protection

必须明确 machineId 生命周期状态机，例如：

FREE
ACTIVE
EXPIRED

或者设计更合理的状态。

### 数据库故障

Snowflake 正常生成 ID 时不能依赖数据库请求。

数据库只能参与：

- 启动获取 machineId
- heartbeat
- 状态同步
- shutdown release

如果 DB 短暂不可用：

- 已获得合法 machineId 的实例应该尽量继续生成
- 但不能无限期在失去租约的情况下继续生成，避免 machineId 被其他实例回收后出现重复

因此请设计合理的：

lease / heartbeat / safety window

并说明：

什么时候继续服务
什么时候进入 degraded
什么时候拒绝生成 ID

---

## 七、生产级 SegmentId 要求

SegmentId 必须做到：

正常 ID 生成阶段：

不访问数据库。

只有当前号段耗尽时：

从数据库申请下一个 segment。

数据库表建议考虑：

namespace
max_id
step
version
updated_at

但你必须先分析 CosId，再决定最终字段。

数据库申请号段必须满足：

多个实例并发执行：

A
B
C

不会拿到重叠号段。

例如：

A -> 1 ~ 10000
B -> 10001 ~ 20000
C -> 20001 ~ 30000

可以使用：

UPDATE ... SET max_id = max_id + step

配合事务、行锁、RETURNING 或 CAS。

必须考虑不同数据库兼容性。

首期至少优先支持：

MySQL

如果抽象设计允许，再兼容 PostgreSQL。

不要为了跨数据库兼容把实现复杂化到难以维护。

---

## 八、生产级 SegmentChainId 要求

这是本次迁移最重要的部分。

必须保留 CosId SegmentChainId 中真正有价值的设计：

- 当前号段本地原子递增
- next segment 异步预取
- safeDistance
- 避免 ID 请求线程访问 DB
- 尽可能 lock-free
- DB 抖动时利用剩余 segment 缓冲
- 预取失败重试
- segment 切换时线程安全
- 防止重复 prefetch
- 防止 nextSegment 被覆盖
- 防止旧 prefetch task 覆盖新状态

请重点研究 CosId 原始实现。

不要重新发明一个“看起来类似”的简化版本。

可以重构，但必须保持正确性。

---

## 九、线程模型

不要无限创建线程。

SegmentChainId 的预取需要：

- 可配置线程池
- 默认共享 Scheduler / Executor
- daemon thread
- graceful shutdown
- bounded queue 或合理的 backpressure
- 明确异常处理

禁止：

每个 namespace 创建一个永久独立线程。

如果 CosId 原实现这样做，也要判断是否适合迁移。

需要给出线程模型设计说明。

---

## 十、数据库可靠性

所有 JDBC 代码必须考虑：

- connection timeout
- statement timeout
- transaction timeout
- deadlock
- duplicate key
- optimistic lock conflict
- transient SQL exception
- retry
- rollback
- DB unavailable

Retry 必须：

- 有最大次数
- exponential backoff
- jitter
- 只重试 transient error

禁止无限循环。

---

## 十一、可观测性

至少提供以下指标接口或 Micrometer 指标：

Snowflake：

- generated_total
- clock_backwards_total
- sequence_overflow_total
- machine_id
- heartbeat_failure_total
- lease_remaining

Segment：

- generated_total
- segment_fetch_total
- segment_fetch_failure_total
- current_segment_remaining
- segment_prefetch_total
- segment_prefetch_failure_total
- segment_switch_total
- hunger_total
- safe_distance

SegmentChain：

必须能观察是否经常进入 hunger。

日志中禁止每个 ID 输出日志。

只记录：

- machineId acquire/release
- segment acquire
- prefetch failure
- clock backwards
- lease lost
- fatal state transition

---

## 十二、配置

Spring Boot 配置需要清晰。

例如：

```yaml
distributed-id:
  snowflake:
    enabled: true
    epoch: 2025-01-01T00:00:00Z
    machine-bits: 10
    sequence-bits: 12

    machine:
      allocator: jdbc
      heartbeat-interval: 10s
      lease-timeout: 30s

  segment:
    enabled: true
    allocator: jdbc
    default-step: 10000

  segment-chain:
    enabled: true
    safe-distance: 0.2
    prefetch:
      retry-count: 3
```

这里只是示意。

你需要根据最终实现重新设计配置。

要求：

- 配置有默认值
- 配置有 validation
- 非法 bit 配置启动失败
- epoch 不合理启动失败
- lease timeout < heartbeat interval 等非法组合启动失败

---

## 十三、数据表

请根据最终设计生成：

MySQL DDL

至少包括：

Snowflake machine allocator table
Segment allocator table

DDL 要考虑：

- primary key
- unique index
- namespace isolation
- machine_id uniqueness
- version
- timestamp precision
- heartbeat index
- reclaim query index

同时说明每个索引的意义。

---

## 十四、测试要求

迁移不是以“代码能编译”为完成标准。

必须提供完整测试。

### 单元测试

Snowflake：

- 单线程唯一性
- 多线程唯一性
- sequence overflow
- clock backwards
- machineId boundary
- epoch boundary

Segment：

- segment boundary
- 多线程
- segment 切换

SegmentChain：

- 并发切换
- prefetch 成功
- prefetch 失败
- DB 延迟
- hunger
- next segment race
- 高频并发

### 数据库集成测试

使用 Testcontainers MySQL。

测试：

100 个实例并发 acquire machineId

验证：

machineId 不重复。

多个实例并发获取 Segment：

验证号段绝不重叠。

### 压测

提供 JMH benchmark：

Snowflake
SegmentId
SegmentChainId

至少比较：

throughput
p50
p99

不要伪造 benchmark 数据。

只提供 benchmark 工程和执行方法。

---

## 十五、Chaos / Failure 场景

至少设计或测试以下场景：

1. DB down 10 秒
2. DB down 超过 lease timeout
3. JVM kill -9
4. 机器时间回拨 100ms
5. 时间回拨 5 秒
6. 两个实例同时启动
7. 100 个实例同时启动
8. heartbeat thread 卡顿
9. segment prefetch DB timeout
10. 当前 segment 仅剩 1% 时 DB down
11. 服务滚动发布
12. machineId reclaim race

针对每种情况说明系统行为。

重点回答：

会不会重复 ID？

会不会停止服务？

会不会只损失可用性而保持一致性？

---

## 十六、代码质量

必须遵守：

- Java 17+
- 清晰命名
- 不滥用继承
- 优先 composition
- 核心类避免依赖 Spring
- 尽量 immutable
- 显式 thread safety
- public API 加 JavaDoc
- 关键并发逻辑写注释
- 不复制 CosId 无关代码
- 不留下 TODO 作为核心逻辑
- 不吞异常
- 不使用 System.out
- 不使用全局可变 static 状态
- 明确 Executor 生命周期
- 明确 close / shutdown

---

## 十七、关于 CosId 源码的使用

CosId 使用 Apache License 2.0。

如果直接复制或实质性修改 CosId 中的源码：

必须保留相应版权和 License 声明。

不要删除原作者声明。

如果只是理解其算法后重新实现，也需要明确哪些代码是：

- copied
- modified
- inspired/reimplemented

最后生成一份：

THIRD_PARTY_NOTICES.md

以及必要的 LICENSE NOTICE。

不要为了规避 License 而做无意义的变量名替换。

---

## 十八、执行方式

不要一次性生成整个系统。

按以下阶段执行，并且每阶段都确保代码可以编译、测试通过后再继续：

Phase 1

分析 CosId 源码和依赖关系。

输出：

MIGRATION_ANALYSIS.md

包含：

- 类关系
- 算法
- 保留部分
- 删除部分
- 风险点

Phase 2

设计新的核心 API。

输出：

ARCHITECTURE.md

然后创建 core 模块。

Phase 3

实现 Snowflake + JdbcMachineIdAllocator。

完成测试。

Phase 4

实现 SegmentId + JdbcSegmentAllocator。

完成测试。

Phase 5

实现 SegmentChainId + PrefetchWorker。

完成并发测试。

Phase 6

Spring Boot Starter。

Phase 7

MySQL schema + Testcontainers integration tests。

Phase 8

Metrics + health + graceful shutdown。

Phase 9

JMH benchmark。

Phase 10

生产级 review。

对整个实现执行一次：

- concurrency review
- correctness review
- failure mode review
- SQL review
- thread leak review
- license review

---

## 十九、每次修改代码时的要求

修改代码之前：

先说明：

1. 当前要解决的问题
2. CosId 原实现怎么做
3. 我们是否保持这个设计
4. 如果改变，为什么

然后再修改。

修改完成后：

必须执行：

- compile
- unit tests
- integration tests（适用时）

如果失败：

自己定位问题并修复。

不要把未验证的代码交给我。

---

## 二十、最重要的设计原则

优先级如下：

第一：绝不生成重复 ID

第二：故障情况下优先保证正确性

第三：高吞吐

第四：低延迟

第五：代码简洁

如果“高可用”和“绝不重复”发生冲突：

宁可临时拒绝生成 ID，也不能生成可能重复的 ID。

不要为了 benchmark 做危险优化。

---

## 二十一、最终交付物

最终项目至少包含：

```text
id-generator/
├── id-generator-core
├── id-generator-jdbc
├── id-generator-spring-boot-starter
├── id-generator-benchmark
├── docs
│   ├── ARCHITECTURE.md
│   ├── MIGRATION_ANALYSIS.md
│   ├── FAILURE_MODES.md
│   └── OPERATIONS.md
├── schema
│   └── mysql.sql
├── THIRD_PARTY_NOTICES.md
├── LICENSE
└── README.md
```

README 至少包含：

- 项目作用
- Snowflake 使用方式
- SegmentId 使用方式
- SegmentChainId 使用方式
- Spring Boot 配置
- MySQL 初始化
- machineId 工作机制
- segment 工作机制
- 故障行为
- 监控指标
- 部署建议

---

## 二十二、现在开始

现在不要直接写代码。

第一步：

扫描当前本地 CosId 仓库。

找出所有和以下关键字直接相关的源码、测试、SQL 和配置：

SnowflakeId
MachineIdDistributor
JdbcMachineIdDistributor
ClockBackwards
MachineState
SegmentId
SegmentChainId
PrefetchWorker
IdSegment
JdbcIdSegmentDistributor

输出完整调用关系和迁移分析。

特别标记：

【必须保留】

【建议重写】

【可以删除】

【存在生产风险】

然后给出新的模块设计方案。

等完成这一步后，再进入实现阶段。