# OPERATIONS

本文记录部署、初始化、监控、故障处理和 benchmark 执行方式。

## MySQL 初始化

生产环境执行：

```powershell
mysql -h 127.0.0.1 -P 3306 -u root -p xiaohashu < schema/mysql.sql
```

本机开发环境可使用：

```yaml
distributed-id:
  jdbc:
    initialize-schema: true
```

生产建议关闭自动初始化：

```yaml
distributed-id:
  jdbc:
    initialize-schema: false
```

## Spring Boot 接入

在业务模块加入：

```xml
<dependency>
    <groupId>com.dyc</groupId>
    <artifactId>xiaohashu-id-generator-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

本项目 `xiaohashu-id-generator-biz` 已在 `application-dev.yml` 接入本机 MySQL：`127.0.0.1:3306/xiaohashu`，用户 `root`，密码 `1234`。

## 推荐配置

```yaml
distributed-id:
  namespace: xiaohashu
  jdbc:
    initialize-schema: false
  snowflake:
    enabled: true
    epoch: 2025-01-01T00:00:00Z
    clock-backwards:
      spin-threshold: 1ms
      max-wait: 500ms
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

## Health

Actuator 存在时自动注册 `distributedIdHealthIndicator`。

健康检查关注：

- DataSource 是否有效。
- Snowflake lease 是否仍可生成。
- Snowflake heartbeat 是否连续失败。
- Segment 当前剩余量和拉取失败次数。
- SegmentChain 是否关闭、预取是否持续失败。

## Metrics

Micrometer 存在时自动注册指标。建议告警：

- `xiaohashu.id.snowflake.heartbeat.failure` 持续增长。
- `xiaohashu.id.snowflake.heartbeat.consecutive.failures` 大于 0 且持续。
- `xiaohashu.id.snowflake.lease.lost` 大于 0。
- `xiaohashu.id.snowflake.clock.backwards` 突增。
- `xiaohashu.id.segment.fetch.failure` 持续增长。
- `xiaohashu.id.segment.current.remaining` 长时间接近 0。
- `xiaohashu.id.segment.chain.prefetch.failure` 持续增长。
- `xiaohashu.id.segment.prefetch.scheduler.closed` 等于 1。

## 发布前检查

```powershell
mvn -pl xiaohashu-id-generator-core,xiaohashu-id-generator-jdbc,xiaohashu-id-generator-spring-boot-starter,xiaohashu-id-generator-benchmark -am test
```

本机 MySQL 集成测试：

```powershell
mvn -pl xiaohashu-id-generator-jdbc -am "-Dtest=LocalMySqlJdbcAllocatorIntegrationTest" "-Ddistributed.id.local-mysql.enabled=true" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Benchmark 工程构建：

```powershell
mvn -pl xiaohashu-id-generator-benchmark -am -DskipTests package
java -jar .\xiaohashu-id-generator-benchmark\target\benchmarks.jar -l
```

正式 benchmark 命令见 `xiaohashu-id-generator-benchmark/README.md`。

## 故障处理

DB 短暂不可用：

- Snowflake 在 lease 有效期内继续发号。
- Segment/SegmentChain 依赖本地剩余号段继续发号。
- 等 DB 恢复后观察失败指标是否停止增长。

DB 超过 lease timeout：

- Snowflake 必须拒绝发号。
- 恢复 DB 后重启实例或重建生成器，不要人工延长本地 lease。

时钟回拨：

- 小回拨由 `ClockBackwardsHandler` 等待。
- 大回拨会抛出 `ClockBackwardsException`。
- 修复 NTP 后再恢复。

优雅停机：

- Spring Boot 正常关闭会触发 `SnowflakeLeaseLifecycle.stop()` 释放 machine lease。
- SegmentChain generator 和 shared scheduler 会通过 `close()` 释放后台线程。
