# xiaohashu-id-generator-biz

基于本地 CosId 源码迁移的自研分布式 ID 服务，当前只保留 JDBC 作为分布式协调和持久化方式。

## 能力

- `SNOWFLAKE`: Snowflake ID，启动分配 `machineId`，正常生成不访问数据库。
- `SEGMENT`: 数据库号段模式，号段内本地原子递增。
- `SEGMENT_CHAIN`: 号段 + 预取 + 低竞争链式设计，保留 CosId 的 `safeDistance` / hunger / prefetch worker 思路。

## MySQL

默认连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/xiaohashu
    username: root
    password: 1234
```

默认会自动创建 `id_generator_machine` 和 `id_generator_segment`。生产环境可先执行 `src/main/resources/schema/mysql.sql`，再设置：

```yaml
distributed-id:
  jdbc:
    initialize-schema: false
```

## API

```http
GET /id-generator/snowflake
POST /id-generator/next
POST /id-generator/batch
```

示例：

```json
{"type":"SEGMENT_CHAIN","size":100}
```

## 配置

配置入口见 `src/main/resources/application-dev.yml`：

- `distributed-id.namespace`
- `distributed-id.snowflake.epoch`
- `distributed-id.snowflake.machine-bit`
- `distributed-id.snowflake.sequence-bit`
- `distributed-id.snowflake.heartbeat-interval`
- `distributed-id.snowflake.safe-guard-duration`
- `distributed-id.segment.step`
- `distributed-id.segment-chain.step`
- `distributed-id.segment-chain.safe-distance`
- `distributed-id.segment-chain.worker-core-pool-size`

## 验证命令

```bash
mvn test -pl xiaohashu-id-generator/xiaohashu-id-generator-biz -am
```
