# 自研分布式 ID 服务架构设计

> 本文是 Phase 2 的架构设计产物。设计基于 `docs/MIGRATION_ANALYSIS.md` 对 CosId 的分析，但新实现采用自研包结构和接口边界；除非后续文件头部明确声明，否则代码为 inspired/reimplemented，不直接复制 CosId 源码。

## 设计目标

自研分布式 ID 服务提供三类生成器：

- `SnowflakeIdGenerator`：基于时间戳、`machineId`、序列号的趋势递增 ID。
- `SegmentIdGenerator`：基于数据库号段的本地递增 ID。
- `SegmentChainIdGenerator`：在号段模式上增加链式预取和低竞争切换。

首期只支持 JDBC/MySQL 作为分布式协调和持久化方式，但核心模块不能依赖 JDBC 或 Spring。生产优先级为：

1. 绝不生成重复 ID。
2. 故障情况下优先保证正确性。
3. 高吞吐。
4. 低延迟。
5. 代码简洁。

## 模块边界

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

### xiaohashu-id-generator-core

只包含算法、模型、异常和抽象接口：

- `IdGenerator`
- `SnowflakeIdGenerator`
- `SegmentIdGenerator`
- `SegmentChainIdGenerator`
- `MachineIdAllocator`
- `SegmentAllocator`
- `MachineState`
- `MachineLease`
- `InstanceIdentity`
- `IdSegment`
- `SegmentChainNode`
- `ClockBackwardsHandler`
- `TimeService`

禁止依赖：

- Spring
- JDBC
- Redis
- ZooKeeper
- MyBatis
- Servlet/Web

### xiaohashu-id-generator-jdbc

实现 JDBC 协调层：

- `JdbcMachineIdAllocator`
- `JdbcSegmentAllocator`
- `MySqlMachineIdRepository`
- `MySqlSegmentRepository`
- `JdbcRetryTemplate`
- `SqlExceptionClassifier`
- `SchemaInitializer`

JDBC 模块负责事务、SQL、重试、rollback 和 MySQL 方言。核心生成器只看 `MachineIdAllocator` 与 `SegmentAllocator`。

### xiaohashu-id-generator-spring-boot-starter

负责 Spring 适配：

- `@ConfigurationProperties`
- AutoConfiguration
- Bean 创建
- 生命周期管理
- graceful shutdown
- health indicator
- Micrometer metrics

Spring 层不实现核心算法。

### xiaohashu-id-generator-benchmark

提供 JMH benchmark 工程，用于真实测量三类 core 生成器：

- `snowflake`
- `segmentId`
- `segmentChainId`

Benchmark 模块只提供工程、runner 和执行方法，不在仓库文档中编造或固化性能数据。当前 benchmark 使用内存版 `SegmentAllocator`，重点观察算法本身的 throughput、p50 和 p99；JDBC/MySQL 性能需要结合具体数据库、连接池和部署环境单独压测。

### xiaohashu-id-generator-biz

保持业务服务入口，负责暴露 HTTP/RPC 接口和接入 Nacos 等微服务能力。它依赖 starter 或组合 core/jdbc，不承载核心算法。

## Core API

### IdGenerator

```java
public interface IdGenerator {
    long nextId();

    default String nextIdAsString() {
        return Long.toString(nextId());
    }
}
```

统一生成器入口。后续 Web/RPC 层只依赖这个最小接口。

### Snowflake

`SnowflakeConfig` 负责 bit layout、epoch、时钟回拨策略的参数校验：

- `timestampBits + machineBits + sequenceBits <= 63`
- `machineBits > 0`
- `sequenceBits > 0`
- `timestampBits > 0`
- `sequenceResetThreshold` 不超过当前序列容量
- `epoch` 必须早于当前时间，且当前时间不能超过 timestamp bit 可表达范围

`SnowflakeIdGenerator` 正常发号不访问数据库。它通过 `MachineLease` 判断当前 `machineId` 是否仍可用：

```text
MachineIdAllocator.acquire(...) -> MachineLease
MachineLeaseMonitor heartbeat -> refresh lease
SnowflakeIdGenerator.nextId()
  -> lease usable?
  -> timestamp rollback?
  -> sequence overflow?
  -> compose id
```

`ClockBackwardsHandler` 只处理本机时间回拨。小回拨可等待，超过阈值必须拒绝生成。

### Machine Lease

`MachineIdAllocator` 是分布式机器号唯一性的抽象：

```java
MachineLease acquire(String namespace, InstanceIdentity instance, int maxMachineId, long lastGeneratedTimestamp);
MachineLease heartbeat(MachineLease lease, long lastGeneratedTimestamp);
void release(MachineLease lease);
```

`MachineLease` 是生成器可读的本地租约快照，至少包含：

- `namespace`
- `machineId`
- `instanceId`
- `status`
- `lastTimestamp`
- `leaseAcquiredAt`
- `leaseExpiresAt`
- `version`

运行期行为：

- `ACTIVE` 且未超过 `leaseExpiresAt`：允许生成。
- heartbeat 失败但仍在本地租约安全窗口：可继续生成，同时状态进入 degraded。
- heartbeat 明确发现租约丢失或超过安全窗口：拒绝生成。

### Segment

`SegmentAllocator` 是号段唯一性的抽象：

```java
IdSegment nextSegment(String namespace, String tag, long requestedStep);
```

`IdSegment` 表示一个闭区间 `[startInclusive, endInclusive]`。本地通过原子递增发号，正常路径不访问数据库。

`SegmentIdGenerator` 行为：

```text
current segment available -> atomic increment -> return
current segment exhausted -> synchronized fetch next segment -> return
DB unavailable and no local id -> fail
```

### SegmentChain

`SegmentChainNode` 是链式号段节点，包含：

- `version`
- `IdSegment`
- `next`

`SegmentChainIdGenerator` 后续实现应保留 Phase 1 中确认的 CosId 关键语义：

- `next` 只能设置一次。
- `head` 只能向更新版本前进。
- 请求线程优先遍历链上可用段。
- 链断或全部耗尽时，请求线程允许同步补链。
- 后台 worker 用 `safeDistance` 保持 head-tail 缓冲。
- hunger 触发预取距离扩张，非 hunger 收缩。
- 预取失败不能污染已有链。

`SegmentChainConfig` 控制：

- `safeDistance`
- `maxPrefetchDistance`
- `prefetchPeriod`
- `prefetchRetryCount`
- `prefetchRetryBackoff`

## JDBC 状态模型

### Machine Table

生产 DDL 位于 `schema/mysql.sql`：

```sql
create table id_machine (
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
) engine=InnoDB;
```

`id_machine_sequence` 用于序列化同一 namespace 下的新机器号分配，避免高并发启动时大量实例同时执行 `max(machine_id) + 1` 造成重复冲突：

```sql
create table id_machine_sequence (
  namespace varchar(64) not null,
  next_machine_id int unsigned not null default 0,
  created_at datetime(3) not null,
  updated_at datetime(3) not null,
  primary key (namespace)
) engine=InnoDB;
```

### Segment Table

```sql
create table id_segment (
  namespace varchar(64) not null,
  tag varchar(64) not null,
  max_id bigint unsigned not null default 0,
  step bigint unsigned not null,
  version bigint unsigned not null default 0,
  last_fetch_at datetime(3) default null,
  updated_at datetime(3) not null,
  created_at datetime(3) not null,
  primary key (namespace, tag)
) engine=InnoDB;
```

## 配置模型

Spring Boot Starter 后续使用 `distributed-id` 前缀：

```yaml
distributed-id:
  namespace: xiaohashu
  jdbc:
    initialize-schema: false
  snowflake:
    enabled: true
    epoch: 2025-01-01T00:00:00Z
    timestamp-bits: 41
    machine-bits: 10
    sequence-bits: 12
    clock-backwards:
      spin-threshold: 1ms
      max-wait: 500ms
  machine:
    allocator: jdbc
    instance-id:
    stable: false
    heartbeat-interval: 10s
    lease-timeout: 30s
    max-heartbeat-failures: 2
    shutdown-timeout: 5s
  segment:
    enabled: true
    tag: note
    default-step: 10000
  segment-chain:
    enabled: true
    tag: note-chain
    default-step: 10000
    safe-distance: 2
    max-prefetch-distance: 1024
    prefetch-period: 1s
    prefetch-retry-count: 3
    prefetch-retry-backoff: 50ms
    scheduler-pool-size: 4
    shutdown-timeout: 5s
```

## 可观测性

Phase 8 提供两层观测能力：

- core 层接口暴露轻量计数和状态，不依赖 Spring/Micrometer。
- Spring Boot starter 在 classpath 存在 Micrometer 或 Actuator 时自动注册 `MeterBinder` 和 `HealthIndicator`。

Snowflake 指标：

- `xiaohashu.id.snowflake.generated`
- `xiaohashu.id.snowflake.clock.backwards`
- `xiaohashu.id.snowflake.sequence.overflow`
- `xiaohashu.id.snowflake.machine.id`
- `xiaohashu.id.snowflake.lease.expires.at`
- `xiaohashu.id.snowflake.heartbeat.success`
- `xiaohashu.id.snowflake.heartbeat.failure`
- `xiaohashu.id.snowflake.heartbeat.consecutive.failures`
- `xiaohashu.id.snowflake.lease.lost`
- `xiaohashu.id.snowflake.lease.release.failure`
- `xiaohashu.id.snowflake.lifecycle.running`

Segment 指标：

- `xiaohashu.id.segment.generated`
- `xiaohashu.id.segment.allocated`
- `xiaohashu.id.segment.fetch.failure`
- `xiaohashu.id.segment.current.remaining`

SegmentChain 指标：

- `xiaohashu.id.segment.chain.prefetch.success`
- `xiaohashu.id.segment.chain.prefetch.failure`
- `xiaohashu.id.segment.chain.distance`
- `xiaohashu.id.segment.chain.prefetch.distance`
- `xiaohashu.id.segment.chain.closed`
- `xiaohashu.id.segment.prefetch.scheduler.failure`
- `xiaohashu.id.segment.prefetch.scheduler.jobs`
- `xiaohashu.id.segment.prefetch.scheduler.closed`

`DistributedIdHealthIndicator` 会检查：

- `DataSource` 连接是否有效。
- Snowflake 当前 lease 是否仍允许发号。
- Snowflake heartbeat 是否持续失败、是否发生 lease lost。
- Segment 当前号段剩余量、分配次数、拉取失败次数。
- SegmentChain 是否已关闭、链路距离和预取失败次数。

如果数据库不可用、Snowflake lease 不可发号，或 SegmentChain 已关闭，health 返回 `OUT_OF_SERVICE`；普通计数型失败会进入 details，便于告警规则按业务阈值判断。

## 优雅关闭

Snowflake 的 `SnowflakeLeaseLifecycle` 在 Spring 停止时先取消 heartbeat，再在 `distributed-id.machine.shutdown-timeout` 内等待 executor 退出，最后释放当前 machine lease。释放失败不阻塞应用退出，但会累加 `releaseFailureTotal` 和对应 Micrometer 指标。

SegmentChain 的 `DefaultSegmentChainIdGenerator` 会在 close 时注销预取任务；共享 `DefaultSegmentPrefetchScheduler` 先执行 graceful shutdown，超过 `distributed-id.segment-chain.shutdown-timeout` 后再强制停止，避免后台预取线程阻塞进程退出。

## 测试策略

Phase 2 只验证 core API 和配置对象可编译、基础校验有效。后续阶段增加：

- Snowflake 单线程、多线程、sequence overflow、clock backwards、epoch boundary。
- Segment boundary、多线程、segment 切换。
- SegmentChain 并发切换、prefetch 成功/失败、DB 延迟、hunger、next race。
- Testcontainers MySQL 并发 machineId acquire 与 segment overlap 测试。
- JMH benchmark：通过 `xiaohashu-id-generator-benchmark` 生成 `target/benchmarks.jar`，分别使用 `thrpt` 和 `sample` 模式观察 throughput、p50、p99。

## License 策略

当前 core 模块采用 inspired/reimplemented。后续若直接复制或实质性改写 CosId 文件，必须保留 Apache License 2.0 版权头，并在 `THIRD_PARTY_NOTICES.md` 记录来源。
