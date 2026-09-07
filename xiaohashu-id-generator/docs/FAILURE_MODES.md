# FAILURE_MODES

本文记录自研分布式 ID 服务的主要故障场景、期望行为和恢复建议。

## 原则

优先级固定为：

1. 绝不生成重复 ID。
2. 故障情况下优先保证正确性。
3. 高吞吐。
4. 低延迟。
5. 代码简洁。

如果可用性和唯一性冲突，生成器必须拒绝生成，而不是冒险继续。

## DB down 10 秒

Snowflake：

- 已获得 `ACTIVE` lease 且 `leaseExpiresAt` 未到期时，可继续本地发号。
- heartbeat 失败会累加 `heartbeatFailureTotal` 和 `consecutiveHeartbeatFailures`。
- 如果失败次数未超过 `maxHeartbeatFailures` 且本地 lease 未过期，发号仍可继续。

SegmentId：

- 当前 `IdSegment` 仍有剩余时继续本地发号。
- 当前号段耗尽后需要访问 DB，DB 不可用时抛出异常，不生成新 ID。

SegmentChainId：

- 已预取链上的号段仍可继续发号。
- 后台预取失败会累加 `prefetchFailureTotal` 与 scheduler failure 指标。
- 链上全部号段耗尽且 DB 仍不可用时，请求线程同步补链失败并抛出异常。

恢复建议：

- 观察 `xiaohashu.id.snowflake.heartbeat.failure`、`xiaohashu.id.segment.fetch.failure`、`xiaohashu.id.segment.chain.prefetch.failure`。
- DB 恢复后 Snowflake heartbeat 和 Segment 拉取会在下一次调用或调度中恢复。

## DB down 超过 lease timeout

Snowflake：

- 本地 `MachineLease.canGenerateAt(now)` 到期后必须拒绝发号。
- heartbeat 连续失败超过 `maxHeartbeatFailures` 时，`SnowflakeLeaseLifecycle` 会将本地 lease 标记为 `EXPIRED`，后续 `nextId()` 抛出 `GeneratorUnavailableException`。

恢复建议：

- 恢复 DB 后重启实例或重新创建生成器，让 `MachineIdAllocator.acquire(...)` 重新获取安全 lease。
- 不要人工把本地实例继续保活，因为旧 `machineId` 可能已被其他实例回收。

## JVM kill -9

行为：

- 无法执行 graceful release。
- DB 中原 `id_machine` 记录保持 `ACTIVE`，直到 `lease_expires_at` 超时。
- 其他非 stable 实例只能在超时后回收该 `machine_id`。
- 回收时使用持久化 `last_timestamp` 与新实例上报 timestamp 的最大值，降低时间回拨导致重复的风险。

部署建议：

- `lease-timeout` 应大于 `heartbeat-interval`，并给网络抖动留余量。
- 容器环境避免使用固定 `instance-id`，减少 kill 后快速重启与旧进程身份混淆。

## 机器时间回拨 100ms

Snowflake：

- 小于等于 `clock-backwards.spin-threshold` 时自旋等待时间追平。
- 大于 spin 阈值但不超过 `clock-backwards.max-wait` 时短暂 park 后重试。
- 期间 `clockBackwardsTotal` 累加。

SegmentId / SegmentChainId：

- 号段 ID 与本机时间无关，不受短时钟回拨直接影响。

## 机器时间回拨 5 秒

Snowflake：

- 超过 `clock-backwards.max-wait` 时抛出 `ClockBackwardsException`，拒绝生成。
- 这是正确性保护，不应通过扩大等待窗口掩盖系统时间问题。

恢复建议：

- 修复 NTP 或宿主机时钟。
- 确认当前时间大于已持久化 `last_timestamp` 后再恢复服务。

## machineId 被回收

行为：

- heartbeat SQL 包含 `namespace`、`machine_id`、`instance_id`、`version`、`status` 和 `lease_expires_at` 条件。
- 如果记录被其他实例回收或版本变化，heartbeat 更新行数不是 1，抛出 `MachineLeaseLostException`。
- lifecycle 捕获后将本地 lease 置为 `EXPIRED`，后续发号失败。

## Segment 首次并发创建

行为：

- 多个实例同时为同一 `(namespace, tag)` 创建首段时，唯一键保证只有一个 insert 成功。
- 失败实例通过 `JdbcRetryTemplate` 重试，下一轮会 `select ... for update` 已存在的行并分配后续号段。

## Segment max_id 溢出

行为：

- `JdbcSegmentAllocator` 使用显式边界检查，`Long.MAX_VALUE - previousMaxId < requestedStep` 时抛出 `SegmentOverflowException`。
- 不会返回跨越 long 上限的号段。

## 线程泄漏

行为：

- Snowflake heartbeat executor 由 `SnowflakeLeaseLifecycle.stop()` 关闭。
- SegmentChain generator close 时注销预取任务。
- `DefaultSegmentPrefetchScheduler.close()` 会先 graceful shutdown，超时后强制停止。

运维要求：

- Spring Boot 场景依赖容器 lifecycle 自动关闭。
- 手动 new `DefaultSegmentChainIdGenerator` 或 scheduler 时必须调用 `close()`。
