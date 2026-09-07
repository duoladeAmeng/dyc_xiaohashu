# THIRD_PARTY_NOTICES

## CosId

本模块中的分布式 ID 核心实现基于本地 CosId 源码迁移，涉及 Snowflake、Segment、SegmentChain、PrefetchWorker、JDBC MachineId/Segment 分发逻辑。

上游项目：

- Name: CosId
- Repository: https://github.com/Ahoo-Wang/CosId
- License: Apache License, Version 2.0
- Copyright: Copyright [2021-present] [ahoo wang <ahoowang@qq.com>]

本次迁移属于 copied and modified / inspired and reimplemented 的混合：

- copied and modified: Snowflake bit layout、sequence overflow、clock backwards synchronizer、`IdSegment`、`DefaultIdSegment`、`IdSegmentChain`、`SegmentChainId`、`PrefetchWorker` 线程模型、JDBC Segment 申请模式。
- inspired and reimplemented: Spring Boot 装配、JDBC schema、MachineId lease/heartbeat 字段、HTTP API、Micrometer 指标、仓库内 DTO 和异常处理。

Apache License 2.0 原文可在上游仓库 `LICENSE` 文件中查看。后续如继续扩大 CosId 迁移范围，应保持对应文件头或在本声明中追加来源说明。
