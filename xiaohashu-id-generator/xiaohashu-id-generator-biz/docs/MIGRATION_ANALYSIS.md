# MIGRATION_ANALYSIS

本文档正文使用中文；类名、配置键、SQL 表名保持英文。

## CosId 源码关系

本次阅读并迁移的本地源码来自 `E:\CodeDir\CosId`：

- `cosid-core`: `SnowflakeId`, `AbstractSnowflakeId`, `MillisecondSnowflakeId`, `ClockBackwardsSynchronizer`, `DefaultClockBackwardsSynchronizer`
- `cosid-core`: `SegmentId`, `DefaultSegmentId`, `IdSegment`, `DefaultIdSegment`, `IdSegmentChain`, `MergedIdSegment`, `SegmentChainId`
- `cosid-core`: `PrefetchWorkerExecutorService`, `DefaultPrefetchWorker`, `PrefetchWorker`, `AffinityJob`
- `cosid-core`: `MachineIdDistributor`, `AbstractMachineIdDistributor`, `InstanceId`, `MachineState`
- `cosid-jdbc`: `JdbcMachineIdDistributor`, `JdbcIdSegmentDistributor`

## Snowflake

【必须保留】

- `timestamp + machineId + sequence` 的 63 bit 正数布局。
- 同一毫秒内 sequence 原子递增，sequence 溢出后等待下一毫秒。
- `lastTimestamp` 回拨检测；轻微回拨等待追平，超过阈值拒绝生成。
- 正常生成路径不访问数据库。
- 启动时通过 JDBC 获取 `machineId`，并把 `lastTimestamp` 写入数据库用于重启/回收保护。
- 心跳守护 `machineId`；超过 lease/safeGuard 后拒绝生成，优先保证不重复。

【建议重写】

- CosId 的 `MachineStateStorage` 本地文件存储没有纳入本服务首期目标，本次用内存状态和 JDBC 表状态配合。
- CosId 原 `cosid_machine` 表较精简；本次保留核心字段，并增加 `status`、`version`、`last_heartbeat` 便于生产排查。

【可以删除】

- Redis、ZooKeeper、Mongo、Kubernetes StatefulSet distributor。
- Proxy、Web、Demo、文档站、Spring Redis starter。
- Radix converter、友好 ID、CosId 字符串编码等非目标能力。

【存在生产风险】

- 机器系统时间大幅回拨时，必须拒绝生成，不能尝试继续服务。
- DB 心跳不可用超过 `safe-guard-duration` 后，实例必须停止 Snowflake 生成，避免 `machineId` 被其他实例回收后重复。

## SegmentId

【必须保留】

- `IdSegment` 范围不可变：`offset = maxId - step`，本地 `sequence` 从 offset 开始递增。
- `DefaultIdSegment` 使用 `AtomicLongFieldUpdater`，正常生成不访问数据库。
- 当前号段耗尽后同步申请新号段。
- JDBC 申请号段保持 CosId 模式：事务内 `update last_max_id = last_max_id + step`，随后 `select last_max_id`。

【建议重写】

- CosId grouped/date segment 属于扩展能力，本次保留 `SegmentAllocator` 扩展点，不迁入 grouped 实现。

【存在生产风险】

- 号段行缺失会导致申请失败；当前服务支持启动自动建表并初始化默认 `segment` / `segment-chain` 行。
- DB 短暂不可用时，已有号段能继续使用；当前号段耗尽仍无法取号时会抛出异常，不会生成重复 ID。

## SegmentChainId

【必须保留】

- `volatile headChain` 作为当前链头。
- `IdSegmentChain.next` 只在 `trySetNext` / `ensureSetNext` 中设置，避免覆盖已预取的 next。
- ID 请求线程优先遍历本地链，只有链上没有可用号段时才同步取新链并触发 hunger。
- `PrefetchWorkerExecutorService` 共享少量 daemon worker，不为每个 namespace 单独创建永久线程。
- `safeDistance` 保持最小预取距离；hunger 时翻倍扩张，空闲时逐步收缩。

【建议重写】

- 保留 CosId 的链式并发结构，去掉 grouped 依赖和 Guava/jspecify/errorprone 注解依赖。
- 日志只保留号段申请、预取失败、hunger 扩缩容等事件，不记录每个 ID。

【存在生产风险】

- DB 抖动期间依赖已预取链缓冲；缓冲耗尽后会阻塞/失败申请，不会生成重复 ID。
- `safeDistance` 配得太小会更频繁进入 hunger；需要结合业务 QPS 调整 `step` 和 `safe-distance`。
