# THIRD_PARTY_NOTICES

本项目的分布式 ID 设计参考了 CosId 的公开实现和测试思路，但当前 `xiaohashu-id-generator-core`、`xiaohashu-id-generator-jdbc`、`xiaohashu-id-generator-spring-boot-starter` 与 `xiaohashu-id-generator-benchmark` 代码为 inspired/reimplemented，没有直接复制 CosId 源码文件。

## CosId

- Project: CosId
- Source path used for analysis: `E:\CodeDir\CosId`
- License: Apache License 2.0
- Usage: 作为 Snowflake、machineId、Segment、SegmentChain 机制的设计参考。

后续如果直接复制或实质性修改 CosId 中的源码，应在对应源文件保留必要版权和许可证声明，并在本文补充具体文件路径、原始来源与修改说明。
