# ARCHITECTURE

## 包结构

- `com.dyc.xiaohashu.id.generator.core`: 生成器核心接口和异常。
- `com.dyc.xiaohashu.id.generator.core.snowflake`: Snowflake 算法。
- `com.dyc.xiaohashu.id.generator.core.machine`: `MachineIdAllocator` 抽象、`InstanceId`、`MachineState`、回拨同步器。
- `com.dyc.xiaohashu.id.generator.core.segment`: `SegmentIdGenerator`、`SegmentChainIdGenerator`、`IdSegment` 链式结构。
- `com.dyc.xiaohashu.id.generator.jdbc`: JDBC allocator、schema initializer、有限 retry。
- `com.dyc.config`: Spring Boot 配置和 Bean 装配。
- `com.dyc.controller` / `com.dyc.service`: 服务 HTTP 出口。

## 扩展点

- 新增 Redis/ZooKeeper/其他数据库 MachineId 方案时，实现 `MachineIdAllocator`。
- 新增 Redis/远程服务 Segment 方案时，实现 `SegmentAllocator`。
- 核心 Snowflake/Segment/SegmentChain 生成器不直接依赖 Spring 和 JDBC。

## 数据库表

`id_generator_machine`：

- `primary key(name)`: `namespace.machineId` 的唯一行。
- `uk_id_generator_machine_namespace_machine`: 保证同一 namespace 内 machineId 唯一。
- `idx_id_generator_machine_instance`: 支持稳定 instance 重启复用查询。
- `idx_id_generator_machine_reclaim`: 支持按 namespace/status/last_timestamp 回收。

`id_generator_segment`：

- `primary key(namespace,name)`: 不同业务号段隔离。
- `last_max_id`: 已分配的最大 ID。
- `version`: 便于观测和未来 CAS 扩展。

## 故障行为

- Snowflake 生成阶段不访问 DB；心跳失败但未超过 `safe-guard-duration` 时继续生成。
- Snowflake 心跳失败超过 `safe-guard-duration` 后拒绝生成，防止 machineId 被回收后重复。
- Segment/SegmentChain 在本地号段耗尽前不访问 DB；DB 不可用时已预取号段继续服务，耗尽后失败。
- 时间轻微回拨等待追平；超过阈值抛错拒绝生成。

## 配置示例

见 `src/main/resources/application-dev.yml`，默认连接本机 MySQL：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/xiaohashu
    username: root
    password: 1234
distributed-id:
  namespace: xiaohashu
```
