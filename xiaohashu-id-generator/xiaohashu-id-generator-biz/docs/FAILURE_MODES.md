# FAILURE_MODES

## DB down 10 秒

- Snowflake：未超过 `safe-guard-duration` 时继续生成；超过后拒绝。
- Segment：当前号段未耗尽继续生成；耗尽后失败。
- SegmentChain：已预取链未耗尽继续生成；耗尽后失败。

## DB down 超过 lease timeout

Snowflake 拒绝生成，保证不与未来回收的 `machineId` 重复。

## JVM kill -9

无法执行 release。其他实例只能在 `last_timestamp` 超过 safeGuard 后回收该 machineId。

## 机器时间回拨

小回拨等待追平；超过 `clock-broken-threshold` 拒绝生成。

## 多实例同时启动

`id_generator_machine` 的 `(namespace,machine_id)` 唯一约束和插入冲突 retry 保证不会拿到相同 machineId。

## 多实例同时申请 Segment

同一行 `update last_max_id = last_max_id + step` 在 InnoDB 中串行执行，事务内读回的 `last_max_id` 不重叠。

## Segment prefetch DB timeout

记录 `prefetch_failure`，请求线程继续使用已有链；链耗尽后同步申请，失败则抛错。
