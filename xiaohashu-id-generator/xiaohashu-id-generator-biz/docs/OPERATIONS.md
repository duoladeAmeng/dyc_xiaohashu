# OPERATIONS

## 初始化

默认 `distributed-id.jdbc.initialize-schema=true`，服务启动会自动创建：

- `id_generator_machine`
- `id_generator_segment`

生产环境也可以先手工执行 `src/main/resources/schema/mysql.sql`，再关闭自动初始化。

## 接口

- `GET /id-generator/snowflake`
- `POST /id-generator/next`，请求体：`{"type":"SEGMENT"}`
- `POST /id-generator/batch`，请求体：`{"type":"SEGMENT_CHAIN","size":100}`

`type` 可选：

- `SNOWFLAKE`
- `SEGMENT`
- `SEGMENT_CHAIN`

## 监控

已注册 Micrometer gauge：

- `id_generator_snowflake_generated_total`
- `id_generator_snowflake_clock_backwards_total`
- `id_generator_snowflake_sequence_overflow_total`
- `id_generator_snowflake_heartbeat_failure_total`
- `id_generator_snowflake_machine_id`
- `id_generator_snowflake_lease_remaining_millis`
- `id_generator_segment_generated_total`
- `id_generator_segment_fetch_total`
- `id_generator_segment_fetch_failure_total`
- `id_generator_segment_switch_total`
- `id_generator_segment_current_remaining`
- `id_generator_segment_chain_generated_total`
- `id_generator_segment_chain_prefetch_total`
- `id_generator_segment_chain_prefetch_failure_total`
- `id_generator_segment_chain_switch_total`
- `id_generator_segment_chain_hunger_total`
- `id_generator_segment_chain_safe_distance`

## 部署建议

- 确保所有实例使用同一个 `distributed-id.namespace`。
- `distributed-id.snowflake.safe-guard-duration` 必须大于 `heartbeat-interval`，建议至少 3 倍。
- 高 QPS 业务优先调大 `segment.step` 和 `segment-chain.step`，再调大 `safe-distance`。
- 生产建议关闭应用 DEBUG 日志，避免 Spring 测试级日志噪声。
