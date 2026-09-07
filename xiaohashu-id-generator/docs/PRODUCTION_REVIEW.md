# PRODUCTION_REVIEW

本文是 Phase 10 的生产级 review 记录。

## Review Scope

审查范围：

- `xiaohashu-id-generator-core`
- `xiaohashu-id-generator-jdbc`
- `xiaohashu-id-generator-spring-boot-starter`
- `xiaohashu-id-generator-benchmark`
- `xiaohashu-id-generator-biz`
- `schema/mysql.sql`

审查维度：

- concurrency review
- correctness review
- failure mode review
- SQL review
- thread leak review
- license review

## 已修复问题

### Snowflake stop 后本地 lease 仍可短暂发号

风险：

`SnowflakeLeaseLifecycle.stop()` 释放 DB lease 后，如果同一 JVM 内仍有代码持有 generator 引用，本地 `MachineLease` 之前仍可能显示 `ACTIVE`，理论上存在释放后继续发号的风险。

修复：

- stop 时先保存原 lease。
- 将 generator 本地 lease 标记为 `EXPIRED`。
- 再使用原 lease 调用 `MachineIdAllocator.release(...)`。
- 新增测试断言 stop 后 `generator.nextId()` 抛出 `GeneratorUnavailableException`。

### benchmark 模块生成 dependency-reduced-pom.xml

风险：

`maven-shade-plugin` 默认会生成 `dependency-reduced-pom.xml`，容易污染工作区。

修复：

- 设置 `createDependencyReducedPom=false`。
- 删除已生成的 `dependency-reduced-pom.xml`。

### biz 模块未接入自研 starter

风险：

核心和 starter 已完成，但 `xiaohashu-id-generator-biz` 未依赖 starter，应用启动后不会自动创建生成器 bean。

修复：

- `xiaohashu-id-generator-biz` 增加 `xiaohashu-id-generator-spring-boot-starter` 依赖。
- 增加 MySQL driver、Druid、Actuator 依赖。
- `application-dev.yml` 增加本机 MySQL 和 `distributed-id` 配置。

### biz AppTest 使用 JUnit 3

风险：

Spring Boot 3 测试栈不包含 `junit.framework`，聚合测试会编译失败。

修复：

- 改为 JUnit 5 smoke test。

## concurrency review

结论：通过，保留以下前提。

- Snowflake `nextId()` 使用 synchronized 串行化本地 timestamp 和 sequence 更新，避免同毫秒 sequence 竞争。
- `MachineLease` 使用 `AtomicReference` 更新，heartbeat 与发号线程之间只交换不可变快照。
- `JdbcMachineIdAllocator` 使用 `id_machine_sequence` 和 `select ... for update` 串行化新 machineId 分配。
- `JdbcSegmentAllocator` 对已有 `(namespace, tag)` 行使用 `select ... for update`，首段并发创建依赖唯一键和 bounded retry。
- `SegmentChainNode.next` 只能设置一次；`head` 和 `tail` 只按 version 前进。
- `DefaultSegmentPrefetchScheduler` 对同一 job 使用 `runQueued` 和 `inFlight` 合并唤醒，避免同一预取任务并发执行。

## correctness review

结论：通过，关键正确性保护已覆盖。

- Snowflake bit layout 校验 `timestampBits + machineBits + sequenceBits <= 63`。
- machineId 超过 Snowflake bit 容量时拒绝启动。
- 时钟大回拨拒绝生成。
- lease 非 `ACTIVE` 或过期时拒绝生成。
- Segment 使用闭区间 `[startInclusive, endInclusive]`，耗尽后才切换下一段。
- Segment max_id 溢出会拒绝分配。
- SegmentChain 预取失败不会修改已有链。

## failure mode review

结论：通过，详细行为见 `docs/FAILURE_MODES.md`。

剩余运维前提：

- 生产节点必须依赖可靠 NTP。
- DB 长时间不可用时 Snowflake 会进入不可用，这是为了保证不重复。
- Segment 类生成器在本地号段耗尽后依赖 DB 恢复。

## SQL review

结论：通过，DDL 和 SQL 路径匹配。

- `id_machine` 主键为 `(namespace, machine_id)`。
- `uk_machine_instance` 保证同一 namespace 下 `instance_id` 唯一。
- `id_machine_sequence` 序列化新增 machineId。
- `idx_machine_reclaim` 支持按 namespace/status/lease_expires_at 回收扫描。
- `id_segment` 主键为 `(namespace, tag)`，保证业务号段隔离。

注意：

- 生产环境建议由 DDL 发布工具管理 `schema/mysql.sql`。
- `distributed-id.jdbc.initialize-schema=true` 只建议用于本地开发和测试。

## thread leak review

结论：通过。

- Snowflake heartbeat executor 是 daemon 线程，并由 Spring lifecycle 关闭。
- Segment prefetch scheduler 使用共享 daemon 线程池，不按 namespace 创建永久独立线程。
- scheduler close 有 graceful timeout，超时后 `shutdownNow()`。
- SegmentChain generator close 时 unregister job。

## license review

结论：通过。

- 当前实现为 inspired/reimplemented，没有直接复制 CosId 源码文件。
- 已补充 `THIRD_PARTY_NOTICES.md`，声明 CosId 作为设计参考，Apache License 2.0。
- 后续如直接复制或实质性改写 CosId 源码，必须在文件头和 `THIRD_PARTY_NOTICES.md` 中补充来源与许可证说明。

## Remaining Risks

- 未实现 Redis、ZooKeeper 或其他协调后端，这是当前范围内的有意限制。
- benchmark 只提供工程和执行方法，不提交性能数据。
- HTTP/RPC 对外接口尚未定义；当前交付侧重核心生成器、JDBC 协调和 Spring Boot bean 接入。
