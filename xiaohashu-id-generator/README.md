# xiaohashu-id-generator

自研分布式 ID 服务，提供 Snowflake、SegmentId、SegmentChainId 三类 ID 生成能力。核心算法位于 `xiaohashu-id-generator-core`，JDBC/MySQL 协调位于 `xiaohashu-id-generator-jdbc`，Spring Boot 接入位于 `xiaohashu-id-generator-spring-boot-starter`。

## Modules

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

## Snowflake 使用方式

Spring Boot 接入时，在应用中依赖 `xiaohashu-id-generator-spring-boot-starter` 并启用：

```yaml
distributed-id:
  namespace: xiaohashu
  snowflake:
    enabled: true
  machine:
    allocator: jdbc
```

业务代码注入：

```java
private final SnowflakeIdGenerator snowflakeIdGenerator;

long id = snowflakeIdGenerator.nextId();
```

Snowflake 启动时通过 `MachineIdAllocator.acquire(...)` 获取 `machineId`，运行期由 `SnowflakeLeaseLifecycle` 定期 heartbeat。发号路径不访问数据库，只检查本地 lease 是否仍安全。

## SegmentId 使用方式

```yaml
distributed-id:
  segment:
    enabled: true
    tag: note
    default-step: 10000
```

业务代码注入 bean 名称 `segmentIdGenerator`：

```java
private final SegmentIdGenerator segmentIdGenerator;

long id = segmentIdGenerator.nextId();
```

当前号段还有剩余 ID 时，本地原子递增返回；号段耗尽后，通过 `SegmentAllocator.nextSegment(...)` 从 MySQL 获取新的非重叠号段。

## SegmentChainId 使用方式

```yaml
distributed-id:
  segment-chain:
    enabled: true
    tag: note-chain
    default-step: 10000
    safe-distance: 2
    max-prefetch-distance: 1024
```

业务代码注入：

```java
private final SegmentChainIdGenerator segmentChainIdGenerator;

long id = segmentChainIdGenerator.nextId();
```

SegmentChainId 在 SegmentId 基础上增加链式预取。后台 worker 尽量保持 `head` 到 `tail` 的安全距离；请求线程遇到链空时允许同步补链，但不会污染已经分配的号段。

## Spring Boot 配置

`xiaohashu-id-generator-biz` 的 dev 环境已接入本机 MySQL：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/xiaohashu?useUnicode=true&characterEncoding=utf-8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 1234
    type: com.alibaba.druid.pool.DruidDataSource
```

完整配置示例见 `xiaohashu-id-generator-spring-boot-starter/src/main/resources/application-example.yml`。

## MySQL 初始化

生产 DDL 位于：

```text
schema/mysql.sql
```

包含：

- `id_machine`
- `id_machine_sequence`
- `id_segment`

本地 dev 可使用 `distributed-id.jdbc.initialize-schema=true` 自动初始化。生产环境建议由 Flyway、Liquibase 或 DBA 发布 DDL，并关闭自动初始化。

## machineId 工作机制

`JdbcMachineIdAllocator` 使用 `id_machine_sequence` 序列化同一 namespace 下的新机器号分配，避免高并发启动时 `max(machine_id) + 1` 类方案产生冲突或 MySQL gap lock 风险。

状态：

- `ACTIVE`：当前实例持有 lease。
- `RELEASED`：实例优雅停止后释放，可回收。
- `EXPIRED`：heartbeat 超时或本地判定失效，可在时间安全检查后回收。

重启时，如果同一 `instance_id` 的记录仍安全，会复用原 machineId；如果 lease 已丢失或超过安全窗口，则拒绝继续用旧 lease 发号。

## segment 工作机制

`JdbcSegmentAllocator` 使用 `select ... for update` 锁住 `(namespace, tag)` 行，更新 `max_id` 后返回闭区间 `[previousMaxId + 1, nextMaxId]`。并发插入首个号段时依靠唯一键和 bounded retry 保证最终号段不重叠。

## 故障行为

详细说明见 `docs/FAILURE_MODES.md`。核心原则是：宁可临时拒绝生成 ID，也不能生成可能重复的 ID。

## 监控指标

starter 在存在 Micrometer 时自动注册指标，在存在 Actuator 时自动注册 `distributedIdHealthIndicator`。主要指标包括：

- `xiaohashu.id.snowflake.generated`
- `xiaohashu.id.snowflake.clock.backwards`
- `xiaohashu.id.snowflake.sequence.overflow`
- `xiaohashu.id.snowflake.heartbeat.failure`
- `xiaohashu.id.segment.current.remaining`
- `xiaohashu.id.segment.fetch.failure`
- `xiaohashu.id.segment.chain.prefetch.failure`
- `xiaohashu.id.segment.prefetch.scheduler.failure`

## 部署建议

- 所有生产节点必须启用可靠 NTP。
- `distributed-id.namespace` 在同一业务域内保持一致，不同环境使用不同 namespace。
- `distributed-id.machine.instance-id` 在容器环境中默认建议不配置，让进程级 ID 随实例变化；固定物理机部署可显式配置并设置 `stable=true`。
- 生产环境关闭 `distributed-id.jdbc.initialize-schema`，通过 DDL 发布流程管理表结构。
- 将 `/actuator/health` 与关键 Micrometer 指标接入告警。
- 发布前运行 `mvn -pl xiaohashu-id-generator-core,xiaohashu-id-generator-jdbc,xiaohashu-id-generator-spring-boot-starter,xiaohashu-id-generator-benchmark -am test`。

## Benchmark

JMH 工程位于 `xiaohashu-id-generator-benchmark`。构建和执行方式见 `xiaohashu-id-generator-benchmark/README.md`。仓库不提交伪造的 benchmark 数据。
