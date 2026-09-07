# 自研分布式 ID 系统设计与使用说明

本文面向本项目维护者，用来彻底理解 `xiaohashu-id-generator` 的设计、实现边界、运行机制、使用方式和生产排障方法。本文不做 CosId 迁移对比，只描述当前自研系统本身。

## 1. 系统定位

`xiaohashu-id-generator` 是小哈书项目内的自研分布式 ID 服务，目标是为 note、user、comment 等业务服务生成全局唯一的 `Long` ID。

它同时提供三类 ID 生成能力：

- `SnowflakeIdGenerator`：基于时间戳、`machineId`、序列号生成趋势递增 ID。
- `SegmentIdGenerator`：基于 MySQL 号段表预分配一批连续 ID，本地递增发号。
- `SegmentChainIdGenerator`：在号段模式上增加后台预取链，减少请求线程等待数据库的概率。

核心原则固定为：

1. 绝不生成重复 ID。
2. 故障情况下优先保证正确性。
3. 在正确性满足后追求高吞吐。
4. 在高吞吐基础上降低延迟。
5. 保持实现边界清晰，不把算法、JDBC、Spring、HTTP 混在一起。

如果可用性和唯一性发生冲突，本系统选择拒绝发号，而不是冒险返回可能重复的 ID。

## 2. 模块结构

```text
xiaohashu-id-generator/
├── xiaohashu-id-generator-api
├── xiaohashu-id-generator-core
├── xiaohashu-id-generator-jdbc
├── xiaohashu-id-generator-spring-boot-starter
├── xiaohashu-id-generator-benchmark
├── xiaohashu-id-generator-biz
├── docs
└── schema
```

### 2.1 xiaohashu-id-generator-api

`api` 模块是给其他微服务远程调用用的 OpenFeign 契约模块。

主要内容：

- `DistributedIdFeignApi`：OpenFeign 客户端接口。
- `ApiConstants`：服务名常量。
- `IdGeneratorType`：对外暴露的发号器类型。
- `GenerateIdReqDTO`：单个 ID 请求 DTO。
- `BatchGenerateIdReqDTO`：批量 ID 请求 DTO。
- `BatchGenerateIdRspDTO`：批量 ID 响应 DTO。

其他服务接入时，只依赖该模块，不需要依赖 `core`、`jdbc` 或 `starter`。

### 2.2 xiaohashu-id-generator-core

`core` 模块只放算法、抽象接口、不可变模型和异常。

它不依赖：

- Spring
- JDBC
- MySQL
- MyBatis
- Redis
- Nacos
- Servlet/Web

这让核心发号算法可以单独测试，也方便以后替换协调后端，例如 Redis、ZooKeeper、etcd。

主要抽象：

- `IdGenerator`
- `SnowflakeIdGenerator`
- `SegmentIdGenerator`
- `SegmentChainIdGenerator`
- `MachineIdAllocator`
- `SegmentAllocator`
- `TimeService`

### 2.3 xiaohashu-id-generator-jdbc

`jdbc` 模块负责 MySQL 持久化和分布式协调。

主要职责：

- 分配和续约 `machineId`。
- 分配不重叠的数据库号段。
- 执行 SQL 事务。
- 对可重试的数据库异常做 bounded retry。
- 初始化本地开发表结构。

核心类：

- `JdbcMachineIdAllocator`
- `JdbcSegmentAllocator`
- `JdbcRetryTemplate`
- `JdbcSchemaInitializer`

### 2.4 xiaohashu-id-generator-spring-boot-starter

`starter` 模块负责把 core/jdbc 接到 Spring Boot。

主要职责：

- 读取 `distributed-id` 配置。
- 根据配置创建发号器 bean。
- 管理 Snowflake machine lease heartbeat。
- 管理 SegmentChain 后台预取线程。
- 注册 Actuator health。
- 注册 Micrometer metrics。
- 应用关闭时做 graceful shutdown。

业务服务如果想在本进程内直接注入发号器，依赖这个 starter 即可。

### 2.5 xiaohashu-id-generator-biz

`biz` 模块是独立部署的 ID 服务。

它依赖：

- `xiaohashu-id-generator-api`
- `xiaohashu-id-generator-spring-boot-starter`
- Spring Web
- Actuator
- MySQL driver
- Druid
- Nacos discovery

它通过 HTTP 接口暴露 ID 生成能力，并由 `api` 模块提供 OpenFeign 调用方式。

### 2.6 xiaohashu-id-generator-benchmark

`benchmark` 模块是 JMH 压测工程，用于测 core 算法层吞吐和延迟。当前 benchmark 重点观测算法本身，不把本地机器结果写死进文档。

## 3. 总体调用链

### 3.1 远程调用链

note 这类服务通过 OpenFeign 调用 ID 服务：

```text
note-biz
  -> DistributedIdFeignApi
  -> Nacos 根据服务名 xiaohashu-distributed-id-generator 找实例
  -> id-generator-biz HTTP Controller
  -> DistributedIdGenerateService
  -> SnowflakeIdGenerator / SegmentIdGenerator / SegmentChainIdGenerator
  -> 返回 Response<Long> 或 Response<BatchGenerateIdRspDTO>
```

服务名来自：

```java
ApiConstants.SERVICE_NAME = "xiaohashu-distributed-id-generator"
```

ID 服务自身的 `application-dev.yml` 中也配置了相同服务名：

```yaml
spring:
  application:
    name: xiaohashu-distributed-id-generator
```

这两个值必须一致，否则 Feign 找不到服务实例。

### 3.2 本地直接调用链

如果某个 Spring Boot 服务不想走 HTTP，而是直接在本进程内使用发号器，可以依赖 starter：

```text
业务代码
  -> 注入 SnowflakeIdGenerator / SegmentIdGenerator / SegmentChainIdGenerator
  -> 本地 nextId()
  -> 必要时访问 MySQL 协调表
```

这种方式延迟更低，但每个业务服务实例都会成为发号节点，需要正确配置 `distributed-id`、数据库和时钟。当前小哈书更推荐由独立 `id-generator-biz` 对外提供统一 RPC。

## 4. 核心接口

### 4.1 IdGenerator

所有发号器都实现最小接口：

```java
public interface IdGenerator {
    long nextId();

    default String nextIdAsString() {
        return Long.toString(nextId());
    }
}
```

这个接口只表达一件事：返回下一个全局唯一 ID。

`nextIdAsString()` 是给前端、日志、跨语言客户端或 JavaScript 精度敏感场景使用的字符串形式。底层仍然是同一个 `Long` ID。

### 4.2 IdGeneratorType

远程 API 通过 `IdGeneratorType` 选择发号器：

```java
public enum IdGeneratorType {
    SNOWFLAKE,
    SEGMENT,
    SEGMENT_CHAIN
}
```

含义：

- `SNOWFLAKE`：高吞吐、趋势递增、正常发号不访问 DB。
- `SEGMENT`：强依赖数据库号段，不依赖本机时间。
- `SEGMENT_CHAIN`：数据库号段 + 后台预取，适合请求线程不希望频繁等待数据库的场景。

## 5. Snowflake 设计

### 5.1 ID 组成

Snowflake ID 使用一个正数 `long`，由三部分组成：

```text
| timestamp delta | machine id | sequence |
```

默认配置：

```yaml
distributed-id:
  snowflake:
    timestamp-bits: 41
    machine-bits: 10
    sequence-bits: 12
    epoch: 2025-01-01T00:00:00Z
```

含义：

- `timestamp-bits=41`：记录当前时间相对 `epoch` 的毫秒差。
- `machine-bits=10`：最多支持 `2^10 = 1024` 个机器号。
- `sequence-bits=12`：同一毫秒、同一机器号内最多支持 `2^12 = 4096` 个序列。
- 三者总和不能超过 63，因为 Java `long` 最高位要保持为 0，避免生成负数。

生成公式可理解为：

```text
id = (timestampDelta << (machineBits + sequenceBits))
   | (machineId << sequenceBits)
   | sequence
```

### 5.2 为什么需要 machineId

不同实例同一毫秒可能生成相同 sequence。如果没有 `machineId`，多实例会撞 ID。

所以 Snowflake 的正确性依赖一个前提：

同一个 `namespace` 下，同一时刻不能有两个存活实例持有同一个 `machineId`。

本系统用 MySQL 表 `id_machine` 加租约机制保证这个前提。

### 5.3 machine lease 获取流程

应用启动时，starter 创建 `SnowflakeIdGenerator` 前会调用：

```java
MachineIdAllocator.acquire(namespace, instanceIdentity, maxMachineId, 0)
```

`JdbcMachineIdAllocator` 会在 MySQL 中查找或创建一条 `id_machine` 记录。

简化流程：

```text
1. 根据 namespace + instance_id 查询旧记录
2. 如果旧记录仍归当前实例且安全，复用原 machine_id
3. 否则尝试回收 RELEASED 或 EXPIRED 的 machine_id
4. 如果没有可回收记录，通过 id_machine_sequence 分配新 machine_id
5. 新 machine_id 不能超过 Snowflake bit layout 允许的最大值
6. 返回 MachineLease 给 SnowflakeIdGenerator
```

`id_machine_sequence` 的作用很关键：它序列化新机器号分配，避免高并发启动时大家同时用 `max(machine_id) + 1` 导致冲突或 MySQL gap lock 风险。

### 5.4 heartbeat 和租约

Snowflake 发号器拿到 `MachineLease` 后，运行期由 `SnowflakeLeaseLifecycle` 定期 heartbeat。

配置：

```yaml
distributed-id:
  machine:
    heartbeat-interval: 10s
    lease-timeout: 30s
    max-heartbeat-failures: 2
```

含义：

- 每 10 秒向数据库续约一次。
- 租约有效期是 30 秒。
- 连续 heartbeat 失败超过 2 次后，本地 lease 会被标记为不可用。

heartbeat SQL 会检查：

- `namespace`
- `machine_id`
- `instance_id`
- `version`
- `status`
- `lease_expires_at`

如果更新行数不是 1，说明租约可能已经丢失，必须停止发号。

### 5.5 Snowflake 发号流程

正常发号路径不访问数据库，只操作本地内存：

```text
nextId()
  -> 检查本地 MachineLease 是否 ACTIVE 且未过期
  -> 读取当前毫秒
  -> 如果当前时间大于 lastTimestamp，sequence 归零或进入低位
  -> 如果当前时间等于 lastTimestamp，sequence + 1
  -> 如果 sequence 用尽，等待下一毫秒
  -> 如果当前时间小于 lastTimestamp，进入时钟回拨处理
  -> 拼接 timestamp + machineId + sequence
  -> 返回 long
```

`nextId()` 内部使用同步控制保护 `lastTimestamp` 和 `sequence`，防止多线程同时更新造成重复。

### 5.6 时钟回拨处理

Snowflake 依赖时间单调前进。如果机器时间回拨，可能和过去某一毫秒的 sequence 重叠。

配置：

```yaml
distributed-id:
  snowflake:
    clock-backwards:
      spin-threshold: 1ms
      max-wait: 500ms
```

行为：

- 回拨小于等于 `spin-threshold`：短暂自旋等待时间追平。
- 回拨大于 `spin-threshold` 但不超过 `max-wait`：短暂 park 后重试。
- 回拨超过 `max-wait`：抛出 `ClockBackwardsException`，拒绝发号。

这不是可用性缺陷，而是唯一性保护。

### 5.7 Snowflake 适用场景

适合：

- noteId、commentId 等高频写入 ID。
- 需要趋势递增、利于索引写入的场景。
- 希望正常请求不访问数据库的高吞吐场景。

注意：

- 必须保证所有生产节点 NTP 正常。
- `epoch` 一旦生产使用，不要随意修改。
- `machine-bits`、`sequence-bits` 改动会改变 ID 布局，生产后要谨慎。

## 6. Segment 设计

### 6.1 基本思想

Segment 发号器把数据库里的一个全局递增值按批次取到本地。

例如当前数据库 `max_id=0`，`default-step=10000`，第一次获取号段：

```text
数据库更新 max_id: 0 -> 10000
本地获得区间: [1, 10000]
```

之后本地直接用 `AtomicLong` 递增：

```text
1, 2, 3, ..., 10000
```

当本地号段耗尽，再访问数据库获取下一段：

```text
数据库更新 max_id: 10000 -> 20000
本地获得区间: [10001, 20000]
```

### 6.2 Segment 数据隔离

号段表主键是：

```text
(namespace, tag)
```

所以不同业务可以用不同 `tag` 隔离号段：

```yaml
distributed-id:
  namespace: xiaohashu
  segment:
    tag: note
```

如果两个业务共用同一个 `(namespace, tag)`，它们会从同一条序列中取 ID，仍然唯一，但业务上不好区分。

当前 `id-generator-biz` 默认配置：

```yaml
distributed-id:
  segment:
    tag: default
  segment-chain:
    tag: default-chain
```

### 6.3 Segment 发号流程

```text
nextId()
  -> 当前 IdSegment 是否还有剩余
  -> 有剩余：本地 atomic increment 返回
  -> 无剩余：进入同步块
  -> 再检查一次当前段是否已经被其他线程刷新
  -> 仍无剩余：调用 SegmentAllocator.nextSegment(...)
  -> 更新 currentSegment
  -> 返回新号段内的第一个 ID
```

请求线程只有在号段耗尽时访问数据库。

### 6.4 数据库分配流程

`JdbcSegmentAllocator` 对已有行执行行锁：

```text
select ... from id_segment where namespace = ? and tag = ? for update
```

然后更新：

```text
previousMaxId = max_id
nextMaxId = previousMaxId + requestedStep
返回 [previousMaxId + 1, nextMaxId]
```

并发安全依赖：

- 主键 `(namespace, tag)` 保证只有一条业务序列行。
- `select ... for update` 保证同一行同一时间只有一个事务修改。
- 首次创建行时依赖唯一键冲突 + retry，保证不会创建两个首段。

### 6.5 Segment 适用场景

适合：

- 不想依赖本机时间的 ID。
- 可以接受号段耗尽时偶尔访问数据库的业务。
- 希望 ID 在某个业务 tag 内连续递增的场景。

注意：

- DB 长时间不可用且本地号段耗尽后，Segment 会拒绝发号。
- `default-step` 越大，访问 DB 越少，但故障或重启时可能浪费更多未使用 ID。
- 号段 ID 保证唯一，不保证无空洞。

## 7. SegmentChain 设计

### 7.1 为什么需要 SegmentChain

普通 Segment 在号段耗尽时，请求线程必须同步访问数据库。高峰期如果很多线程刚好撞在号段切换点，会出现一次明显的延迟尖刺。

SegmentChain 通过后台预取把未来号段提前挂到链上，让请求线程大多数时候只在内存中切换节点。

### 7.2 链路模型

链路由多个 `SegmentChainNode` 组成：

```text
head -> node(v1, [1, 10000]) -> node(v2, [10001, 20000]) -> tail
```

每个节点包含：

- `version`
- `IdSegment`
- `next`

核心约束：

- `next` 只能设置一次。
- `head` 只能向更新版本前进。
- `tail` 只能向更新版本前进。
- 预取失败不能破坏已有链。

这些约束是为了保证多个线程同时发号、预取、切换时不会丢号、重复或链路回退。

### 7.3 SegmentChain 发号流程

```text
nextId()
  -> 从 head 对应节点尝试取 ID
  -> 当前节点有剩余：返回
  -> 当前节点耗尽且 next 存在：head 前进到 next，继续尝试
  -> 当前节点耗尽且 next 不存在：请求线程同步补一个新节点
  -> 补链成功后继续取 ID
  -> 补链失败：抛出异常，不生成 ID
```

后台预取只提升性能，不改变正确性。即使后台预取全部失败，请求线程仍能在链空时同步补链；如果数据库也不可用，则拒绝发号。

### 7.4 预取参数

```yaml
distributed-id:
  segment-chain:
    safe-distance: 2
    max-prefetch-distance: 1024
    prefetch-period: 1s
    prefetch-retry-count: 3
    prefetch-retry-backoff: 50ms
    scheduler-pool-size: 4
```

含义：

- `safe-distance`：希望 `head` 到 `tail` 至少保留多少个后备号段。
- `max-prefetch-distance`：预取距离最多扩张到多少段。
- `prefetch-period`：后台检查和预取频率。
- `prefetch-retry-count`：单次预取失败后的重试次数。
- `prefetch-retry-backoff`：重试间隔。
- `scheduler-pool-size`：后台预取线程池大小。

### 7.5 hunger 与预取距离

当请求线程发现链上后备段不足或需要同步补链时，系统认为出现 hunger。

行为：

- 发生 hunger：说明预取太保守，预取距离会尝试扩张。
- 长时间没有 hunger：说明预取充足，距离可以收缩。

这样能在高峰期提前多拉一些号段，低峰期减少无谓预取。

### 7.6 SegmentChain 适用场景

适合：

- 高峰写入明显的业务。
- 不希望请求线程频繁等待数据库。
- 可以接受提前预留更多号段的场景。

注意：

- 它仍然依赖 MySQL 分配号段。
- DB 长时间不可用且链上号段耗尽后，会拒绝发号。
- 后台预取越激进，重启或故障时浪费的未使用 ID 越多。

## 8. MySQL 表结构

DDL 位于：

```text
schema/mysql.sql
```

### 8.1 id_machine

```sql
create table if not exists id_machine (
  namespace varchar(64) not null,
  machine_id int unsigned not null,
  instance_id varchar(128) not null,
  status varchar(16) not null,
  stable boolean not null default false,
  last_timestamp bigint unsigned not null default 0,
  last_heartbeat_at datetime(3) not null,
  lease_expires_at datetime(3) not null,
  version bigint unsigned not null default 0,
  created_at datetime(3) not null,
  updated_at datetime(3) not null,
  primary key (namespace, machine_id),
  unique key uk_machine_instance (namespace, instance_id),
  key idx_machine_stable (namespace, stable),
  key idx_machine_reclaim (namespace, status, lease_expires_at),
  key idx_machine_heartbeat (namespace, last_heartbeat_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
```

字段解释：

- `namespace`：逻辑命名空间，用于隔离环境或业务域。
- `machine_id`：Snowflake 使用的机器号。
- `instance_id`：实例身份。默认由 hostname + JVM runtime name 生成，也可以显式配置。
- `status`：机器号状态，例如 `ACTIVE`、`RELEASED`、`EXPIRED`。
- `stable`：实例身份是否稳定。物理机固定实例可设为 true，容器环境通常 false。
- `last_timestamp`：该实例上报的最后发号时间，用于 machineId 回收时做安全判断。
- `last_heartbeat_at`：最后续约时间。
- `lease_expires_at`：租约过期时间。
- `version`：乐观版本号，防止旧实例续约覆盖新实例。
- `created_at` / `updated_at`：审计字段。

### 8.2 id_machine_sequence

```sql
create table if not exists id_machine_sequence (
  namespace varchar(64) not null,
  next_machine_id int unsigned not null default 0,
  created_at datetime(3) not null,
  updated_at datetime(3) not null,
  primary key (namespace)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
```

作用：

- 为每个 `namespace` 维护下一个可分配的 `machine_id`。
- 用行锁串行化机器号分配。
- 避免并发启动时使用 `max(machine_id) + 1` 带来的冲突和 gap lock 问题。

### 8.3 id_segment

```sql
create table if not exists id_segment (
  namespace varchar(64) not null,
  tag varchar(64) not null,
  max_id bigint unsigned not null default 0,
  step bigint unsigned not null,
  version bigint unsigned not null default 0,
  last_fetch_at datetime(3) default null,
  updated_at datetime(3) not null,
  created_at datetime(3) not null,
  primary key (namespace, tag)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
```

字段解释：

- `namespace`：逻辑命名空间。
- `tag`：业务序列标识，例如 `note`、`comment`。
- `max_id`：当前已经分配出去的最大 ID。
- `step`：该 tag 最近使用的步长。
- `version`：版本号。
- `last_fetch_at`：最近一次分配号段时间。
- `created_at` / `updated_at`：审计字段。

## 9. Spring Boot 自动装配

starter 的核心配置类是 `DistributedIdAutoConfiguration`。

自动装配行为：

- 存在 `DataSource` 时创建 JDBC allocator。
- `distributed-id.snowflake.enabled=true` 时创建 `SnowflakeIdGenerator`。
- `distributed-id.segment.enabled=true` 时创建名为 `segmentIdGenerator` 的 `SegmentIdGenerator`。
- `distributed-id.segment-chain.enabled=true` 时创建 `SegmentChainIdGenerator` 和共享预取 scheduler。
- 存在 Actuator 时注册 health indicator。
- 存在 Micrometer 时注册 metrics。

注意：

`SegmentChainIdGenerator` 继承自 `SegmentIdGenerator`，所以同时启用两者时，按类型注入 `SegmentIdGenerator` 可能有歧义。需要普通 Segment 时使用 bean 名称：

```java
@Qualifier("segmentIdGenerator")
private SegmentIdGenerator segmentIdGenerator;
```

## 10. 配置说明

### 10.1 当前 dev 配置

`xiaohashu-id-generator-biz/src/main/resources/application-dev.yml` 已接入本机 MySQL：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/xiaohashu?useUnicode=true&characterEncoding=utf-8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 1234
    type: com.alibaba.druid.pool.DruidDataSource
  cloud:
    nacos:
      server-addr: 127.0.0.1:8848
  application:
    name: xiaohashu-distributed-id-generator

server:
  port: 8085
```

ID 配置：

```yaml
distributed-id:
  namespace: xiaohashu
  jdbc:
    initialize-schema: true
  snowflake:
    enabled: true
    epoch: 2025-01-01T00:00:00Z
  machine:
    allocator: jdbc
    stable: false
    heartbeat-interval: 10s
    lease-timeout: 30s
    max-heartbeat-failures: 2
    shutdown-timeout: 5s
  segment:
    enabled: true
    tag: default
    default-step: 10000
  segment-chain:
    enabled: true
    tag: default-chain
    default-step: 10000
    safe-distance: 2
    max-prefetch-distance: 1024
    prefetch-period: 1s
    prefetch-retry-count: 3
    prefetch-retry-backoff: 50ms
    scheduler-pool-size: 4
    shutdown-timeout: 5s
```

### 10.2 namespace

```yaml
distributed-id:
  namespace: xiaohashu
```

`namespace` 是所有协调表的第一层隔离维度。

建议：

- 本地开发：`xiaohashu`
- 测试环境：`xiaohashu-test`
- 预发环境：`xiaohashu-pre`
- 生产环境：`xiaohashu-prod`

不要让不同环境共用同一个 `namespace`。

### 10.3 jdbc.initialize-schema

```yaml
distributed-id:
  jdbc:
    initialize-schema: true
```

含义：

- `true`：应用启动时自动执行表初始化逻辑，适合本地开发。
- `false`：不自动初始化，适合生产。

生产建议通过 DBA、Flyway 或 Liquibase 管理 `schema/mysql.sql`，不要让应用启动时自动建表。

### 10.4 machine 配置

```yaml
distributed-id:
  machine:
    allocator: jdbc
    instance-id:
    stable: false
    heartbeat-interval: 10s
    lease-timeout: 30s
    max-heartbeat-failures: 2
    shutdown-timeout: 5s
```

字段解释：

- `allocator`：当前只支持 `jdbc`。
- `instance-id`：实例 ID。不配置时由 starter 自动生成。
- `stable`：实例 ID 是否稳定。
- `heartbeat-interval`：heartbeat 周期。
- `lease-timeout`：租约超时时间，必须大于 `heartbeat-interval`。
- `max-heartbeat-failures`：连续 heartbeat 失败多少次后本地标记不可用。
- `shutdown-timeout`：停机时等待 heartbeat 线程关闭的时间。

容器环境建议：

```yaml
instance-id:
stable: false
```

物理机固定部署可以考虑：

```yaml
instance-id: id-generator-node-01
stable: true
```

### 10.5 snowflake 配置

```yaml
distributed-id:
  snowflake:
    enabled: true
    epoch: 2025-01-01T00:00:00Z
    timestamp-bits: 41
    machine-bits: 10
    sequence-bits: 12
    sequence-reset-threshold:
    clock-backwards:
      spin-threshold: 1ms
      max-wait: 500ms
```

生产重点：

- `epoch` 必须早于当前时间。
- `epoch` 生产后不要改。
- `machine-bits` 决定同一 namespace 可同时持有的机器号数量。
- `sequence-bits` 决定单机同毫秒最大容量。
- `timestamp-bits + machine-bits + sequence-bits <= 63`。

### 10.6 segment 配置

```yaml
distributed-id:
  segment:
    enabled: true
    tag: default
    default-step: 10000
```

生产重点：

- 不同业务建议使用不同 `tag`。
- `default-step` 根据写入吞吐调整。
- 步长越大，DB 压力越小，但未使用 ID 浪费越多。

### 10.7 segment-chain 配置

```yaml
distributed-id:
  segment-chain:
    enabled: true
    tag: default-chain
    default-step: 10000
    safe-distance: 2
    max-prefetch-distance: 1024
    prefetch-period: 1s
    prefetch-retry-count: 3
    prefetch-retry-backoff: 50ms
    scheduler-pool-size: 4
    shutdown-timeout: 5s
```

生产重点：

- `safe-distance` 越大，请求线程越不容易等待数据库。
- `max-prefetch-distance` 过大可能导致预留太多未使用 ID。
- `scheduler-pool-size` 要根据启用的 generator 数量和数据库能力调整。

## 11. 对外 HTTP / OpenFeign 接口

### 11.1 接口列表

`DistributedIdFeignApi.PREFIX`：

```text
/id-generator
```

接口：

```text
GET  /id-generator/snowflake
GET  /id-generator/snowflake/string
GET  /id-generator/segment
GET  /id-generator/segment/string
GET  /id-generator/segment-chain
GET  /id-generator/segment-chain/string
POST /id-generator/next
POST /id-generator/next/string
POST /id-generator/batch
```

### 11.2 单个 ID

请求：

```http
POST /id-generator/next
Content-Type: application/json

{
  "type": "SNOWFLAKE"
}
```

响应：

```json
{
  "success": true,
  "message": null,
  "errorCode": null,
  "data": 238713713090560
}
```

### 11.3 单个字符串 ID

请求：

```http
POST /id-generator/next/string
Content-Type: application/json

{
  "type": "SEGMENT_CHAIN"
}
```

响应：

```json
{
  "success": true,
  "message": null,
  "errorCode": null,
  "data": "10001"
}
```

### 11.4 批量 ID

请求：

```http
POST /id-generator/batch
Content-Type: application/json

{
  "type": "SEGMENT_CHAIN",
  "size": 10
}
```

响应：

```json
{
  "success": true,
  "message": null,
  "errorCode": null,
  "data": {
    "type": "SEGMENT_CHAIN",
    "size": 10,
    "ids": [10001, 10002, 10003]
  }
}
```

示例里的 `ids` 只展示了前三个，真实响应会返回 `size` 个 ID。

批量限制：

- `size >= 1`
- `size <= 1000`

### 11.5 快捷 GET 接口

如果调用方不想传 JSON，可以直接使用固定类型接口：

```http
GET /id-generator/snowflake
GET /id-generator/segment
GET /id-generator/segment-chain
```

它们分别等价于：

```json
{"type":"SNOWFLAKE"}
{"type":"SEGMENT"}
{"type":"SEGMENT_CHAIN"}
```

## 12. note 服务接入示例

### 12.1 增加依赖

在 `xiaohashu-note-biz/pom.xml` 中增加：

```xml
<dependency>
    <groupId>com.dyc</groupId>
    <artifactId>xiaohashu-id-generator-api</artifactId>
</dependency>
```

根工程已在 dependencyManagement 中管理版本，不需要在 note 里写 `<version>`。

### 12.2 确认 Feign 扫描

当前 note 应用已经有：

```java
@EnableFeignClients(basePackages = "com.dyc.xiaohashu")
```

`DistributedIdFeignApi` 包名是：

```text
com.dyc.xiaohashu.id.generator.api
```

所以当前扫描范围已经覆盖它。

### 12.3 创建 RPC 封装

建议在 note-biz 中新增一个薄封装，避免业务代码到处直接判断 `Response`。

```java
package com.dyc.xiaohashu.note.rpc;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.id.generator.api.DistributedIdFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DistributedIdRpcService {

    @Resource
    private DistributedIdFeignApi distributedIdFeignApi;

    public Long nextNoteId() {
        Response<Long> response = distributedIdFeignApi.nextSnowflakeId();
        if (Objects.isNull(response) || !response.isSuccess() || Objects.isNull(response.getData())) {
            throw new IllegalStateException("获取 noteId 失败");
        }
        return response.getData();
    }
}
```

然后在发布笔记逻辑里使用：

```java
Long noteId = distributedIdRpcService.nextNoteId();
```

### 12.4 批量接入示例

```java
BatchGenerateIdReqDTO request = BatchGenerateIdReqDTO.builder()
        .type(IdGeneratorType.SNOWFLAKE)
        .size(100)
        .build();

Response<BatchGenerateIdRspDTO> response = distributedIdFeignApi.batchGenerateIds(request);
if (response == null || !response.isSuccess() || response.getData() == null) {
    throw new IllegalStateException("批量获取 ID 失败");
}

List<Long> ids = response.getData().getIds();
```

### 12.5 调用类型建议

默认建议：

- noteId：优先 `nextSnowflakeId()`。
- commentId：优先 `nextSnowflakeId()`。
- 对时间不敏感、希望业务内连续递增的特殊序列：考虑 `SEGMENT`。
- 高峰批量写入、希望减少切换延迟：考虑 `SEGMENT_CHAIN`。

## 13. ID 服务启动方式

### 13.1 前置条件

本机开发环境：

- MySQL：`127.0.0.1:3306`
- 用户：`root`
- 密码：`1234`
- 数据库：`xiaohashu`
- Nacos：`127.0.0.1:8848`

### 13.2 启动 id-generator-biz

在仓库根目录执行：

```powershell
mvn -pl xiaohashu-id-generator/xiaohashu-id-generator-biz -am spring-boot:run
```

或进入模块目录：

```powershell
cd E:\CodeDir\xiaohashu\xiaohashu-id-generator\xiaohashu-id-generator-biz
mvn spring-boot:run
```

默认端口：

```text
8085
```

### 13.3 本机 HTTP 验证

Snowflake：

```powershell
curl http://127.0.0.1:8085/id-generator/snowflake
```

Segment：

```powershell
curl http://127.0.0.1:8085/id-generator/segment
```

SegmentChain：

```powershell
curl http://127.0.0.1:8085/id-generator/segment-chain
```

批量：

```powershell
curl -H "Content-Type: application/json" -d "{\"type\":\"SNOWFLAKE\",\"size\":10}" http://127.0.0.1:8085/id-generator/batch
```

PowerShell 中如果 `curl` 是别名，也可以使用：

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8085/id-generator/batch -ContentType "application/json" -Body '{"type":"SNOWFLAKE","size":10}'
```

## 14. 监控与健康检查

### 14.1 Actuator health

配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  endpoint:
    health:
      show-details: when_authorized
```

检查：

```powershell
curl http://127.0.0.1:8085/actuator/health
```

health 会关注：

- `DataSource` 是否可用。
- Snowflake lease 是否仍允许生成。
- Snowflake heartbeat 是否连续失败。
- Segment 当前号段剩余量和拉取失败次数。
- SegmentChain 是否关闭、链距离和预取失败次数。

如果数据库不可用、Snowflake lease 不可发号，或 SegmentChain 已关闭，health 会返回 `OUT_OF_SERVICE`。

### 14.2 Micrometer 指标

关键指标：

```text
xiaohashu.id.snowflake.generated
xiaohashu.id.snowflake.clock.backwards
xiaohashu.id.snowflake.sequence.overflow
xiaohashu.id.snowflake.heartbeat.success
xiaohashu.id.snowflake.heartbeat.failure
xiaohashu.id.snowflake.heartbeat.consecutive.failures
xiaohashu.id.snowflake.lease.lost
xiaohashu.id.snowflake.lifecycle.running
xiaohashu.id.segment.generated
xiaohashu.id.segment.allocated
xiaohashu.id.segment.fetch.failure
xiaohashu.id.segment.current.remaining
xiaohashu.id.segment.chain.prefetch.success
xiaohashu.id.segment.chain.prefetch.failure
xiaohashu.id.segment.chain.distance
xiaohashu.id.segment.chain.prefetch.distance
xiaohashu.id.segment.chain.closed
xiaohashu.id.segment.prefetch.scheduler.failure
xiaohashu.id.segment.prefetch.scheduler.jobs
xiaohashu.id.segment.prefetch.scheduler.closed
```

建议告警：

- `xiaohashu.id.snowflake.heartbeat.consecutive.failures > 0` 且持续增长。
- `xiaohashu.id.snowflake.lease.lost > 0`。
- `xiaohashu.id.snowflake.clock.backwards` 突增。
- `xiaohashu.id.segment.fetch.failure` 持续增长。
- `xiaohashu.id.segment.current.remaining` 长时间接近 0。
- `xiaohashu.id.segment.chain.prefetch.failure` 持续增长。
- `xiaohashu.id.segment.prefetch.scheduler.closed = 1`。

## 15. 故障行为

### 15.1 MySQL 短暂不可用

Snowflake：

- 已拿到 lease 且本地 lease 未过期时，仍可继续本地发号。
- heartbeat 失败计数会增长。
- MySQL 恢复后 heartbeat 会继续尝试恢复。

Segment：

- 当前本地号段有剩余时继续发号。
- 当前号段耗尽后需要访问 DB，如果 DB 仍不可用，会抛异常。

SegmentChain：

- 链上已有号段可以继续发号。
- 后台预取会失败并累加失败指标。
- 链上号段耗尽且 DB 不可用时，请求失败。

### 15.2 MySQL 不可用超过 lease-timeout

Snowflake：

- 本地 lease 过期后必须拒绝发号。
- 这是为了防止该 `machineId` 被其他实例回收后产生重复。

恢复方式：

- 恢复 MySQL。
- 重启 ID 服务实例或重建生成器。
- 不要人工延长本地 lease。

### 15.3 JVM kill -9

行为：

- 无法执行 graceful release。
- `id_machine` 中原记录继续保持 `ACTIVE`，直到 `lease_expires_at` 超时。
- 其他实例只能在超时后回收该 `machine_id`。

这是租约模型允许的故障恢复路径。

### 15.4 时钟回拨

Snowflake：

- 小回拨等待。
- 大回拨拒绝。

Segment / SegmentChain：

- 不依赖本机时间，不受短时时钟回拨直接影响。

生产要求：

- 所有节点必须开启可靠 NTP。
- 不要在生产中手工调系统时间。

### 15.5 machineId 被回收

如果旧实例恢复后尝试 heartbeat，但 DB 中记录已经变成其他实例持有，heartbeat 更新行数会不是 1。

此时：

- `SnowflakeLeaseLifecycle` 会把本地 lease 标记为 `EXPIRED`。
- 后续 `nextId()` 抛出 `GeneratorUnavailableException`。
- 对外接口返回失败响应，不生成 ID。

## 16. 生产部署建议

### 16.1 数据库

生产先执行：

```powershell
mysql -h <host> -P 3306 -u <user> -p <database> < schema/mysql.sql
```

生产配置：

```yaml
distributed-id:
  jdbc:
    initialize-schema: false
```

### 16.2 时钟

Snowflake 节点必须有可靠 NTP。建议监控：

- NTP 同步状态。
- 系统时间跳变。
- `xiaohashu.id.snowflake.clock.backwards`。

### 16.3 多实例

多实例部署时：

- 所有实例使用同一个生产 `namespace`。
- 所有实例连接同一个协调 MySQL。
- 所有实例 `spring.application.name` 相同。
- 不同环境使用不同 `namespace` 和注册中心环境。

### 16.4 灰度与回滚

上线新版本前：

- 先确认 `schema/mysql.sql` 已发布。
- 确认 `/actuator/health` 正常。
- 调用三个 GET 发号接口确认返回成功。
- 观察 heartbeat、segment fetch、prefetch 指标是否正常。

回滚时：

- 使用 Spring Boot 正常停机，触发 graceful shutdown。
- 不要直接 kill -9，除非节点已经卡死。
- 回滚后确认旧实例重新获取 lease 成功。

## 17. 测试与验证

### 17.1 完整模块测试

```powershell
cd E:\CodeDir\xiaohashu\xiaohashu-id-generator
mvn -q test
```

### 17.2 本机 MySQL 集成测试

```powershell
cd E:\CodeDir\xiaohashu\xiaohashu-id-generator
mvn -q -pl xiaohashu-id-generator-jdbc -am "-Dtest=LocalMySqlJdbcAllocatorIntegrationTest" "-Ddistributed.id.local-mysql.enabled=true" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

### 17.3 API/biz 测试

```powershell
cd E:\CodeDir\xiaohashu
mvn -q -pl xiaohashu-id-generator/xiaohashu-id-generator-api,xiaohashu-id-generator/xiaohashu-id-generator-biz -am test
```

### 17.4 Benchmark

构建：

```powershell
cd E:\CodeDir\xiaohashu\xiaohashu-id-generator
mvn -q -pl xiaohashu-id-generator-benchmark -am -DskipTests package
```

查看 benchmark 列表：

```powershell
java -jar .\xiaohashu-id-generator-benchmark\target\benchmarks.jar -l
```

执行时要注意：

- benchmark 结果和 CPU、JDK、操作系统、电源策略有关。
- 当前 benchmark 使用内存版 allocator，主要观察 core 算法。
- JDBC/MySQL 性能需要单独做压测。

## 18. 常见问题

### 18.1 为什么 Snowflake 正常发号不访问 DB

因为 DB 只负责分配和续约 `machineId`。只要本地 lease 仍安全，唯一性由 `timestamp + machineId + sequence` 保证。

这样可以把高频发号路径留在内存里，减少数据库压力。

### 18.2 为什么 Segment 会有 ID 空洞

实例一次拿到 `[1, 10000]`，如果只用了前 100 个就重启，剩下 9900 个不会再被其他实例复用。

这会产生空洞，但不会重复。分布式 ID 系统通常只承诺唯一，不承诺连续无空洞。

### 18.3 为什么生产不建议自动建表

自动建表适合本地开发。生产表结构应该经过审核、发布和回滚流程，避免应用启动时悄悄改数据库结构。

### 18.4 为什么批量接口限制 1000

限制 `size` 可以避免某个调用方一次请求过多 ID，造成长时间占用请求线程或瞬间拉取大量号段。

如果未来确实需要更大批量，建议结合调用方场景、超时配置和数据库能力再调整。

### 18.5 什么时候选三种发号器

默认选 `SNOWFLAKE`。

需要业务 tag 内自然递增、不依赖本机时间时选 `SEGMENT`。

需要号段模式但更在意切换延迟时选 `SEGMENT_CHAIN`。

### 18.6 ID 是否可以暴露给前端

可以，但 JavaScript 对大整数有精度问题。如果前端要展示或传递 ID，建议使用字符串接口：

```text
GET /id-generator/snowflake/string
POST /id-generator/next/string
```

后端之间调用仍优先使用 `Long`。

## 19. 学习代码的建议顺序

如果你想从代码层面彻底掌握，建议按这个顺序读：

1. `core/IdGenerator.java`：看最小抽象。
2. `core/snowflake/SnowflakeConfig.java`：看 Snowflake 位布局和边界校验。
3. `core/snowflake/DefaultSnowflakeIdGenerator.java`：看发号主流程。
4. `core/machine/MachineLease.java`：看租约如何保护发号。
5. `jdbc/JdbcMachineIdAllocator.java`：看 machineId 如何分配、续约、释放。
6. `core/segment/IdSegment.java`：看闭区间号段模型。
7. `core/segment/DefaultSegmentIdGenerator.java`：看本地号段切换。
8. `jdbc/JdbcSegmentAllocator.java`：看 MySQL 如何分配不重叠号段。
9. `core/segment/DefaultSegmentChainIdGenerator.java`：看链式预取和 head/tail 前进。
10. `spring-boot-starter/autoconfigure/DistributedIdAutoConfiguration.java`：看 Spring 如何创建 bean。
11. `biz/controller/DistributedIdController.java`：看 HTTP 对外接口。
12. `api/DistributedIdFeignApi.java`：看其他服务如何远程调用。

读完这条线，基本就能从“业务服务调用一个 Feign 方法”一路追到“MySQL 中某行如何被锁住并更新”。

## 20. 当前生产可用性结论

从代码结构和测试覆盖看，当前系统已经具备进入联调和预发压测的条件：

- core 算法边界清晰。
- JDBC 分配路径有并发和 MySQL 集成测试。
- Spring Boot starter 已接入配置、health、metrics、graceful shutdown。
- biz 已暴露 HTTP/OpenFeign 接口。
- 本机 MySQL 配置已完成。
- `mvn -q test` 已通过。

但正式生产前仍建议完成：

- 预发环境多实例启动测试。
- 真实 MySQL 压测。
- note 等调用方联调。
- Nacos 注册发现验证。
- 监控告警接入。
- NTP 和数据库高可用确认。

这不是否定当前实现，而是分布式 ID 属于基础设施，生产上线前必须用环境级验证补上代码单测无法覆盖的部分。
