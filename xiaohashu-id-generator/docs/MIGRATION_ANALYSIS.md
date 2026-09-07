# CosId 子集迁移分析

> 本文只分析 CosId 源码、调用关系和迁移方案，不实现代码。用户请求来源是 `xiaohashu-id-generator-biz/src/main/java/CosId 子集迁移到自研分布式 ID 服务的 AI 主提示词.md`，其中的内容作为任务说明处理；CosId 仓库中的源码、测试、文档只作为被分析对象，不作为本仓库运行时指令。

## 范围

- 目标仓库：`E:\CodeDir\xiaohashu\xiaohashu-id-generator`
- 目标服务当前骨架：`E:\CodeDir\xiaohashu\xiaohashu-id-generator\xiaohashu-id-generator-biz`
- CosId 源码：`E:\CodeDir\CosId`
- 首期协调介质：JDBC/MySQL，本机连接信息为 `localhost:3306`，用户 `root`，密码 `1234`，数据库 `xiaohashu`

本阶段扫描并分析以下 CosId 关键能力：`SnowflakeId`、`MachineIdDistributor`、`JdbcMachineIdDistributor`、`ClockBackwards`、`MachineState`、`SegmentId`、`SegmentChainId`、`PrefetchWorker`、`IdSegment`、`JdbcIdSegmentDistributor`。

## 源码定位

### Snowflake 相关

- `cosid-core/src/main/java/me/ahoo/cosid/snowflake/SnowflakeId.java`
- `cosid-core/src/main/java/me/ahoo/cosid/snowflake/AbstractSnowflakeId.java`
- `cosid-core/src/main/java/me/ahoo/cosid/snowflake/MillisecondSnowflakeId.java`
- `cosid-core/src/main/java/me/ahoo/cosid/snowflake/SecondSnowflakeId.java`
- `cosid-core/src/main/java/me/ahoo/cosid/snowflake/ClockSyncSnowflakeId.java`
- `cosid-core/src/main/java/me/ahoo/cosid/snowflake/exception/ClockBackwardsException.java`
- `cosid-core/src/main/java/me/ahoo/cosid/snowflake/exception/ClockTooManyBackwardsException.java`
- `cosid-core/src/main/java/me/ahoo/cosid/snowflake/exception/TimestampOverflowException.java`
- `cosid-core/src/test/java/me/ahoo/cosid/snowflake/MillisecondSnowflakeIdTest.java`
- `cosid-core/src/test/java/me/ahoo/cosid/machine/ClockSyncSnowflakeIdTest.java`
- `cosid-core/src/test/java/me/ahoo/cosid/machine/DefaultClockBackwardsSynchronizerTest.java`
- `cosid-core/src/jmh/java/me/ahoo/cosid/SnowflakeIdBenchmark.java`

### MachineId 相关

- `cosid-core/src/main/java/me/ahoo/cosid/machine/MachineIdDistributor.java`
- `cosid-core/src/main/java/me/ahoo/cosid/machine/MachineIdDistribute.java`
- `cosid-core/src/main/java/me/ahoo/cosid/machine/AbstractMachineIdDistributor.java`
- `cosid-core/src/main/java/me/ahoo/cosid/machine/MachineState.java`
- `cosid-core/src/main/java/me/ahoo/cosid/machine/MachineStateStorage.java`
- `cosid-core/src/main/java/me/ahoo/cosid/machine/InstanceId.java`
- `cosid-core/src/main/java/me/ahoo/cosid/machine/ClockBackwardsSynchronizer.java`
- `cosid-core/src/main/java/me/ahoo/cosid/machine/DefaultClockBackwardsSynchronizer.java`
- `cosid-core/src/main/java/me/ahoo/cosid/machine/MachineIdGuarder.java`
- `cosid-core/src/main/java/me/ahoo/cosid/machine/DefaultMachineIdGuarder.java`
- `cosid-core/src/main/java/me/ahoo/cosid/machine/GuardDistribute.java`
- `cosid-jdbc/src/main/java/me/ahoo/cosid/jdbc/JdbcMachineIdDistributor.java`
- `cosid-jdbc/src/main/java/me/ahoo/cosid/jdbc/JdbcMachineIdInitializer.java`
- `cosid-test/src/main/java/me/ahoo/cosid/test/machine/distributor/MachineIdDistributorSpec.java`
- `cosid-jdbc/src/test/java/me/ahoo/cosid/jdbc/JdbcMachineIdDistributorTest.java`

### Segment 相关

- `cosid-core/src/main/java/me/ahoo/cosid/segment/SegmentId.java`
- `cosid-core/src/main/java/me/ahoo/cosid/segment/DefaultSegmentId.java`
- `cosid-core/src/main/java/me/ahoo/cosid/segment/IdSegment.java`
- `cosid-core/src/main/java/me/ahoo/cosid/segment/DefaultIdSegment.java`
- `cosid-core/src/main/java/me/ahoo/cosid/segment/MergedIdSegment.java`
- `cosid-core/src/main/java/me/ahoo/cosid/segment/IdSegmentChain.java`
- `cosid-core/src/main/java/me/ahoo/cosid/segment/SegmentChainId.java`
- `cosid-core/src/main/java/me/ahoo/cosid/segment/concurrent/AffinityJob.java`
- `cosid-core/src/main/java/me/ahoo/cosid/segment/concurrent/PrefetchWorker.java`
- `cosid-core/src/main/java/me/ahoo/cosid/segment/concurrent/DefaultPrefetchWorker.java`
- `cosid-core/src/main/java/me/ahoo/cosid/segment/concurrent/PrefetchWorkerExecutorService.java`
- `cosid-jdbc/src/main/java/me/ahoo/cosid/jdbc/JdbcIdSegmentDistributor.java`
- `cosid-jdbc/src/main/java/me/ahoo/cosid/jdbc/JdbcIdSegmentInitializer.java`
- `cosid-jdbc/src/main/java/me/ahoo/cosid/jdbc/JdbcIdSegmentDistributorFactory.java`
- `cosid-test/src/main/java/me/ahoo/cosid/test/segment/distributor/IdSegmentDistributorSpec.java`
- `cosid-core/src/test/java/me/ahoo/cosid/segment/DefaultSegmentIdTest.java`
- `cosid-core/src/test/java/me/ahoo/cosid/segment/SegmentChainIdTest.java`
- `cosid-core/src/test/java/me/ahoo/cosid/segment/concurrent/PrefetchWorkerExecutorServiceTest.java`
- `cosid-core/src/jmh/java/me/ahoo/cosid/SegmentIdBenchmark.java`
- `cosid-jdbc/src/jmh/java/me/ahoo/cosid/jdbc/MySqlIdBenchmark.java`
- `cosid-jdbc/src/jmh/java/me/ahoo/cosid/jdbc/MySqlChainIdBenchmark.java`

### SQL 与配置

- `cosid-jdbc/src/main/init-script/init-cosid-mysql.sql`
- `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/snowflake/SnowflakeIdProperties.java`
- `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/machine/MachineProperties.java`
- `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/machine/CosIdMachineAutoConfiguration.java`
- `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/machine/CosIdJdbcMachineIdDistributorAutoConfiguration.java`
- `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/machine/CosIdMachineIdLifecycle.java`
- `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/machine/MachineIdHealthIndicator.java`
- `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/segment/SegmentIdProperties.java`
- `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/segment/CosIdSegmentAutoConfiguration.java`
- `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/segment/CosIdJdbcSegmentAutoConfiguration.java`
- `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/segment/SegmentIdBeanRegistrar.java`
- `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/segment/CosIdLifecyclePrefetchWorkerExecutorService.java`

## 调用关系

### Snowflake 调用链

```text
Spring Boot 配置
  SnowflakeIdProperties
  MachineProperties
      |
      v
CosIdMachineAutoConfiguration
  -> InstanceId
  -> MachineStateStorage
  -> ClockBackwardsSynchronizer
  -> GuardDistribute
      |
      v
SnowflakeIdBeanRegistrar
  -> guardDistribute.distribute(namespace, machineBit, instanceId, safeGuardDuration)
  -> new MillisecondSnowflakeId(...) / new SecondSnowflakeId(...)
  -> optional new ClockSyncSnowflakeId(...)
      |
      v
SnowflakeId.generate()
  AbstractSnowflakeId.generate()
    -> getCurrentTime()
    -> currentTimestamp < lastTimestamp: throw ClockBackwardsException
    -> sequence = (sequence + 1) & maxSequence
    -> sequence == 0: nextTime() spin until time > lastTimestamp
    -> diffTimestamp overflow check
    -> compose: diffTimestamp << timestampLeft | machineId << machineLeft | sequence
```

`ClockSyncSnowflakeId` 是装饰器：捕获 `ClockBackwardsException`，调用 `ClockBackwardsSynchronizer.syncUninterruptibly(lastTimestampAsMilliseconds)` 等待本机时钟追平，然后重试一次。

### MachineId 调用链

```text
GuardDistribute.distribute(...)
  -> MachineIdDistributor.distribute(...)
  -> MachineIdGuarder.register(namespace, instanceId)

AbstractMachineIdDistributor.distribute(...)
  -> MachineStateStorage.get(namespace, instanceId)
  -> 本地存在: 校验 machineBit + 启动时钟回拨等待 + 返回
  -> 本地不存在: distributeRemote(...)
  -> 远端返回 last_timestamp 若在未来: 等待时钟追平
  -> MachineStateStorage.set(...)

JdbcMachineIdDistributor.distributeRemote(...)
  -> distributeBySelf: 根据 namespace + instance_id + safeGuardAt 找回自己
  -> distributeByRevert: 找 instance_id='' 或 last_timestamp<=safeGuardAt 的可回收记录
  -> distributeMachine: select max(machine_id)+1 后 insert 新记录

DefaultMachineIdGuarder.start()
  -> scheduleWithFixedDelay(safeGuard)
  -> distributor.guard(namespace, instanceId, safeGuardDuration)
  -> JdbcMachineIdDistributor.guardRemote(...)

CosIdMachineIdLifecycle.stop()
  -> machineIdGuarder.stop()
  -> machineIdDistributor.revert(...)
```

### SegmentId 调用链

```text
SegmentIdBeanRegistrar
  -> IdSegmentDistributorFactory.create(IdSegmentDistributorDefinition)
  -> create SegmentId:
       mode=SEGMENT -> DefaultSegmentId
       mode=CHAIN   -> SegmentChainId

DefaultSegmentId.generate()
  -> step == 1: 每次直接 maxIdDistributor.nextMaxId()
  -> 当前 segment 可用: segment.incrementAndGet()
  -> 当前 segment 不可用或抢到溢出:
       synchronized(this)
       -> 二次检查当前 segment
       -> maxIdDistributor.nextIdSegment(ttl)
       -> segment.ensureNextIdSegment(next)
       -> segment = next
       -> 循环生成

JdbcIdSegmentDistributor.nextMaxId(step)
  -> connection.setAutoCommit(false)
  -> update cosid set last_max_id=last_max_id+?, last_fetch_time=unix_timestamp() where name=?
  -> select last_max_id from cosid where name=?
  -> commit
```

### SegmentChainId 调用链

```text
SegmentChainId constructor
  -> headChain = IdSegmentChain.newRoot(...)
  -> prefetchJob = new PrefetchJob(headChain)
  -> prefetchWorkerExecutorService.submit(prefetchJob)

SegmentChainId.generate()
  -> currentChain = headChain
  -> 遍历链表:
       可用 segment -> incrementAndGet -> forward(currentChain) -> return id
       不可用 -> currentChain = currentChain.getNext()
  -> 链上无可用 segment:
       preIdSegmentChain = headChain
       preIdSegmentChain.trySetNext(preChain -> generateNext(preChain, safeDistance))
       成功则 forward(nextChain)
       prefetchJob.hungry()
       继续循环

PrefetchJob.prefetch()
  -> 根据 Clock.SYSTEM.secondTime() - lastHungerTime 判断 hunger
  -> hunger: prefetchDistance *= 2，上限 100_000_000
  -> 非 hunger: prefetchDistance /= 2，下限 safeDistance
  -> 找到 headChain 之后第一个可用链节点
  -> forward(availableHeadChain)
  -> headToTailGap = availableHeadChain.gap(tailChain, distributor.step)
  -> safeGap = safeDistance - headToTailGap
  -> safeGap <= 0 且非 hunger: 不预取
  -> 否则 appendChain(availableHeadChain, hunger ? prefetchDistance : safeGap)

IdSegmentChain.trySetNext(...)
  -> next 已存在则返回 false
  -> synchronized(this)
  -> 生成 next chain
  -> setNext(next)

IdSegmentChain.ensureSetNext(...)
  -> 从当前节点向后走，直到某个节点 trySetNext 成功
```

## Snowflake 算法分析

CosId 默认毫秒级 bit layout 是 `timestamp=41`、`machine=10`、`sequence=12`，总计 63 bit，最高位不使用以保证正数。默认 epoch 是 CosId 自定义时间戳；当前迁移建议改为业务显式配置，默认可采用 `2025-01-01T00:00:00Z`，启动时校验不能晚于当前时间，也不能导致可用年限过短。

ID 组装方式是：

```text
((currentTime - epoch) << (machineBit + sequenceBit))
| (machineId << sequenceBit)
| sequence
```

同一毫秒内通过 `sequence = (sequence + 1) & maxSequence` 递增。`sequence` 回到 `0` 代表本时间单位序列耗尽，进入 `nextTime()`，自旋等待 `currentTime > lastTimestamp`。如果当前时间小于 `lastTimestamp`，`AbstractSnowflakeId` 抛出 `ClockBackwardsException`；`ClockSyncSnowflakeId` 捕获后等待时钟追平，再重试。

`DefaultClockBackwardsSynchronizer` 的策略：

- 回拨不大于 `spinThreshold` 时，用 `Thread.onSpinWait()` 自旋。
- 回拨大于 `spinThreshold` 且不超过 `brokenThreshold` 时，睡眠回拨毫秒数。
- 回拨超过 `brokenThreshold` 时抛 `ClockTooManyBackwardsException`，拒绝生成 ID。

`lastTimestamp` 有两个用途：

- 运行期检测时钟回拨，避免同一 `machineId` 重新进入已经使用过的时间窗口。
- 启动时由 MachineId 分配器返回上次持有者记录的 `last_timestamp`，如在未来则等待追平，避免机器重启或 `machineId` 回收后重复。

`instanceId` 是运行实例身份，通常由 `host:port` 或显式字符串组成。`stable=true` 表示实例身份稳定，CosId 的语义是优先让同一个稳定实例复用同一个 `machineId`，即使释放也不会清空 `instance_id`；`stable=false` 表示动态实例，优雅退出时可以清空 `instance_id` 供别人回收。

## JdbcMachineIdDistributor 分析

CosId 表结构：

```text
cosid_machine(
  name varchar(100) primary key,        -- {namespace}.{machine_id}
  namespace varchar(100),
  machine_id integer unsigned,
  last_timestamp bigint unsigned,
  instance_id varchar(100),
  distribute_time bigint unsigned,
  revert_time bigint unsigned
)
idx_namespace(namespace)
idx_instance_id(instance_id)
```

分配策略：

- 先查 `namespace + instance_id + last_timestamp > safeGuardAt`，尝试找回当前实例已有记录。
- 再查 `instance_id='' or last_timestamp<=safeGuardAt`，逐个用 `update ... where name=? and (instance_id='' or last_timestamp<=?)` 抢占可回收记录。
- 如果没有可回收记录，执行 `select max(machine_id)+1`，然后 `insert` 新机器号。
- 并发插入冲突时捕获唯一键异常并递归重试。

回收策略：

- 非稳定实例 `revert` 时把 `instance_id` 置为空字符串，并把 `last_timestamp` 写为本地记录的最近时间。
- 稳定实例 `revert` 时保留 `instance_id`，使同一实例下次重启继续使用原 `machineId`。

心跳策略：

- `guard` 只更新 `last_timestamp`。
- 更新条件为 `namespace + instance_id + machine_id`，如果影响行数为 `0`，认为 `MachineIdLostException`。

【必须保留】

- `namespace` 隔离。
- 启动时读取远端 `last_timestamp` 并等待本机时钟追平。
- 同一个稳定 `instanceId` 优先复用同一个 `machineId`。
- 非稳定实例可通过超时和优雅释放进入可回收池。
- 心跳失败后必须进入不可继续生成 ID 的 fatal/lost 状态。
- 多实例并发分配必须靠数据库唯一约束或行锁保证同一 namespace 内 `machine_id` 不重复。

【建议重写】

- 将 `safeGuardDuration` 重构为显式 `lease_timeout`、`heartbeat_interval`、`lease_expires_at`，不要混用 `last_timestamp` 作为心跳与 Snowflake 最近发号时间。
- 新增 `status`、`version`、`last_heartbeat_at`、`updated_at` 字段，状态建议为 `ACTIVE`、`RELEASED`、`EXPIRED`。
- `acquire` 使用事务和有限重试：先复用自己，再抢占过期或释放记录，再创建新记录。MySQL 首期可用 `SELECT ... FOR UPDATE SKIP LOCKED` 或唯一键 + CAS 方案。
- 心跳应延长租约并同步 `last_timestamp`，失败次数超过阈值或当前时间超过本地租约安全窗口后停止发号。
- 异常分类只重试 transient SQL 异常，指数退避加 jitter，禁止无限递归。

【可以删除】

- `ManualMachineIdDistributor` 作为生产默认能力。
- Kubernetes StatefulSet、Redis、ZooKeeper、Mongo、Proxy 相关实现。
- `MachineStateStorage` 的本地文件实现。自研服务首期让 JDBC 成为 machine 状态权威；本地只保留内存 lease 状态。
- CosId provider、converter、sharding、annotation 扫描等框架层。

【存在生产风险】

- `select max(machine_id)+1` 在高并发下依赖 insert 冲突递归重试，极端情况下容易形成热点和递归过深。
- `last_timestamp` 同时承担最近发号时间和心跳时间，语义混杂；若发号停止但 guard 继续更新，则无法准确表达 Snowflake 生成进度。
- `guard` 没有 `version` 或 `lease_expires_at` 条件，不能表达“我仍在有效租约内延长租约”的强语义。
- `DefaultMachineIdGuarder` 记录失败但不直接阻止 `SnowflakeId.generate()`，需要在自研服务里把 lease 状态接入生成路径。
- CosId 对 DB 故障期间已获 machineId 的继续服务边界不够显式。自研服务必须定义 `lease_remaining` 和 `max_offline_duration`。

## SegmentId 分析

CosId 的 `IdSegment` 表示 `[offset + 1, maxId]` 闭区间，构造时 `offset=maxId-step`，`sequence=offset`，每次 `incrementAndGet()` 使用 `AtomicLongFieldUpdater` 原子递增。若 `sequence >= maxId` 或递增后 `nextSeq > maxId`，返回 `SEQUENCE_OVERFLOW=-1`。

`DefaultSegmentId` 正常生成不访问数据库；只有当前 `segment` 不可用时，进入 `synchronized(this)`，二次检查后申请下一个号段。`step==1` 时 CosId 直接每次访问 distributor，这个分支不适合高吞吐生产 ID 服务，建议首期不作为默认路径。

CosId JDBC 表结构：

```text
cosid(
  name varchar(100) primary key,        -- {namespace}.{name}
  last_max_id bigint unsigned,
  last_fetch_time bigint unsigned
)
```

JDBC 申请号段 SQL：

```text
update cosid set last_max_id=(last_max_id + ?), last_fetch_time=unix_timestamp() where name = ?;
select last_max_id from cosid where name = ?;
```

两条语句在同一连接事务中执行，`commit` 后返回新的 `last_max_id`。MySQL/InnoDB 对同一主键行的 `update` 会加行锁，因此多个实例并发申请同一 `name` 的号段时会串行推进，不会重叠。业务隔离通过 `namespace.name` 合并成单列主键实现。

【必须保留】

- 当前号段本地原子递增，正常发号不访问 DB。
- DB 只负责推进 `max_id`，返回新的 `maxId`，本地计算 `start = oldMaxId + 1`。
- 多实例并发依赖数据库行级更新串行化，同一 namespace/tag 绝不重叠。
- 当前号段耗尽且 DB 不可用时，普通 `SegmentId` 必须失败；未耗尽时可继续生成。
- namespace/tag 隔离必须是表结构一等字段或唯一键，不建议继续只拼接单列。

【建议重写】

- 表结构改为 `namespace`、`tag`、`max_id`、`step`、`version`、`updated_at`，唯一键 `(namespace, tag)`。
- MySQL 申请号段优先采用单事务 `SELECT ... FOR UPDATE` + `UPDATE ... WHERE version=?` 或 `UPDATE ... SET max_id=max_id+?, version=version+1 WHERE namespace=? AND tag=?` 后在同事务读取；失败时有限重试。
- `step` 可由配置提供，也可按行存储。建议行内 `step` 作为默认步长，调用方可以传入 `requestedStep`，但必须限制最大值。
- 加入溢出校验：`max_id + step` 不能超过 `Long.MAX_VALUE`。
- 记录 `allocated_from`、`allocated_to`、`allocator_instance_id` 可选审计信息，便于事故排查。

【可以删除】

- Grouped segment、按日期分组、converter、StringSegmentId 适配。
- `step==1` 直接访问 DB 的性能退化路径，除非作为测试用 allocator 保留。
- 自定义 SQL 字符串注入式配置。生产首期只支持 MySQL 方言，减少可维护性成本。

【存在生产风险】

- CosId `JdbcIdSegmentDistributor` 没有显式 rollback，依赖 connection close 的驱动行为；自研实现应 `try/catch` 中明确 rollback。
- 对 `SQLException` 不分类，全部包装为运行时异常；自研实现需要 transient/non-transient 分类和有限重试。
- `last_fetch_time=unix_timestamp()` 秒级精度不足，不利于高并发观测。
- `name={namespace}.{name}` 拼接会引入分隔符歧义，查询和索引也不如复合唯一键清晰。

## SegmentChainId 分析

`SegmentChainId` 的价值在于：请求线程大多数时候只对本地 `AtomicLong` 做递增；后台 `PrefetchWorker` 维护 head 到 tail 的安全距离，DB 抖动时可以消耗已预取的链上号段。

核心结构：

- `IdSegmentChain` 包含 `version`、`idSegment`、`volatile next`、`allowReset`。
- `headChain` 是请求线程当前观察到的链头，`volatile`。
- `tailChain` 在 `PrefetchJob` 内维护，代表已预取链尾。
- `trySetNext` 先无锁检查 `next`，再对当前链节点加 `synchronized`，确保一个节点只接上一个 next。
- `ensureSetNext` 在 next 已存在时沿链向后走，直到能追加到最新尾部，避免覆盖已预取链。
- `forward` 无全局锁，只保证 head 版本向前；非 reset 模式下还要求新链段 offset 大于当前 head。

预取触发：

- 定时触发：`DefaultPrefetchWorker` 周期遍历所有 `AffinityJob`，执行 `prefetch()`。
- 被动触发：请求线程发现链上没有可用 segment 时，会同步补一段并调用 `prefetchJob.hungry()`，记录饥饿时间并 `unpark` worker。

`safeDistance` 与 hunger：

- `safeDistance` 是最低安全距离，单位是基础 `step` 段数量，不是 ID 数量。
- 正常时计算 `headToTailGap`，若小于 `safeDistance`，补齐 `safeGap`。
- 如果最近 `hungerThreshold=5s` 内出现饥饿，`prefetchDistance` 翻倍，上限 `100_000_000`。
- 如果不饥饿，`prefetchDistance` 每轮减半，下限回到 `safeDistance`。
- hunger 状态的本质是“请求线程已经被迫走到链尾甚至同步访问 DB”，说明后台预取速度低于消耗速度。

并发正确性：

- 重复 ID：由 `IdSegment` 的原子递增和 DB 分配的非重叠号段保证。
- 多线程重复 prefetch：同一链节点的 `trySetNext` 只允许一次成功；其他线程沿 next 后移。
- next 被覆盖：`next` 只在 `null` 时设置一次。
- 旧 prefetch 覆盖新状态：链节点不可变，`headChain` 只向 version 更大方向推进；旧任务无法把 next 改回旧值。
- 当前 segment 耗尽但 next 未加载：请求线程会同步 `trySetNext(generateNext(...))`，失败则进入下一轮并唤醒 worker。
- DB 短暂不可用：链上仍有可用段时继续发号；链耗尽且 DB 不可用时失败，保证不重复。

线程模型：

- `PrefetchWorkerExecutorService.DEFAULT` 默认按 CPU 数创建多个 `DefaultPrefetchWorker`。
- worker 是 daemon `Thread`，内部用 `CopyOnWriteArraySet` 存 jobs，循环执行后 `LockSupport.parkNanos(prefetchPeriod)`。
- job 通过轮询绑定到 worker，并在首次提交时启动线程。
- starter 用 `CosIdLifecyclePrefetchWorkerExecutorService` 在 Spring stop 时调用 shutdown。

【必须保留】

- `IdSegmentChain` 的单向链、`version`、`volatile next`、节点级 CAS/锁语义。
- 请求线程优先遍历链上可用段，不在正常路径访问 DB。
- 链断或全部耗尽时，请求线程允许同步补链，作为最后防线。
- `safeDistance`、hunger 动态扩张与收缩。
- `head` 只能前进，`next` 只能设置一次。
- 预取失败不能污染现有链；已有可用段继续服务。

【建议重写】

- 将 `DefaultPrefetchWorker extends Thread` 改为受控 `ScheduledExecutorService` 或专用 `ThreadPoolExecutor + DelayQueue`。
- 增加每个 job 的 `prefetchInFlight` 标志，防止同一 namespace 因定时和 hunger 触发堆积多个 DB 任务。
- 对预取失败加有限重试、退避和指标，不在 worker 主循环里无限静默吞异常。
- 暴露 `safe_distance`、`prefetch_distance`、`hunger_total`、`current_segment_remaining`、`tail_gap` 指标。
- 增加关闭语义：关闭后取消 job，不再接受预取；请求线程在已有段耗尽后拒绝发号。

【可以删除】

- Grouped/日期分组链路。
- `allowReset` 默认能力。生产 ID 生成器不应允许回退 reset，除非专门为分组场景设计。
- 全局静态 `PrefetchWorkerExecutorService.DEFAULT`。Spring 服务应由容器管理共享 executor。

【存在生产风险】

- `prefetchDistance` 最大值过大，极端饥饿会一次申请大量号段，导致 ID 浪费和 DB 突刺。
- `tailChain` 只在 `PrefetchJob` 内普通字段维护；当前 CosId 依赖单 worker 亲和性降低竞争，但自研实现若多线程执行同一 job 必须补充 `inFlight` 或原子状态。
- worker 队列没有背压概念，job 数多时周期遍历延迟不可控。
- `hungerThreshold`、`MAX_PREFETCH_DISTANCE` 写死，不利于生产调参。
- 预取异常只记录日志，业务侧若长期饥饿需要通过指标和 health 明确暴露。

## 新模块设计方案

建议不照搬 CosId 模块结构，而是在 `xiaohashu-id-generator` 下逐步调整为：

```text
xiaohashu-id-generator/
├── xiaohashu-id-generator-api
├── xiaohashu-id-generator-core
├── xiaohashu-id-generator-jdbc
├── xiaohashu-id-generator-spring-boot-starter
├── xiaohashu-id-generator-benchmark
├── xiaohashu-id-generator-biz
├── docs
└── schema
```

`xiaohashu-id-generator-biz` 保持业务服务入口，依赖 starter 或直接依赖 core/jdbc；真正算法不要放在 biz 模块里。

### xiaohashu-id-generator-core

职责：纯算法和抽象，不依赖 Spring/JDBC/Redis/ZooKeeper。

建议包：

```text
com.dyc.xiaohashu.id.core
├── IdGenerator
├── snowflake
│   ├── SnowflakeIdGenerator
│   ├── SnowflakeConfig
│   ├── ClockBackwardsHandler
│   ├── ClockBackwardsStrategy
│   └── TimeService
├── machine
│   ├── MachineIdAllocator
│   ├── MachineLease
│   ├── MachineState
│   ├── InstanceIdentity
│   └── MachineLeaseMonitor
├── segment
│   ├── SegmentIdGenerator
│   ├── SegmentChainIdGenerator
│   ├── IdSegment
│   ├── SegmentChainNode
│   ├── SegmentAllocator
│   └── SegmentPrefetchScheduler
└── metrics
    └── IdGeneratorMetrics
```

核心接口草案：

```java
public interface IdGenerator {
    long nextId();
}

public interface MachineIdAllocator {
    MachineLease acquire(String namespace, InstanceIdentity instance, int maxMachineId, long startupLastTimestamp);
    MachineLease heartbeat(MachineLease lease, long lastGeneratedTimestamp);
    void release(MachineLease lease);
}

public interface SegmentAllocator {
    IdSegment nextSegment(String namespace, String tag, long requestedStep);
}
```

核心生成器需要显式检查关闭状态、租约状态和配置合法性。Snowflake 运行期不访问 DB，但 `nextId()` 必须能读取本地 `MachineLease` 是否仍在安全窗口内。

### xiaohashu-id-generator-jdbc

职责：JDBC 适配、SQL、事务、MySQL 方言、schema 初始化。

建议包：

```text
com.dyc.xiaohashu.id.jdbc
├── JdbcMachineIdAllocator
├── JdbcSegmentAllocator
├── JdbcRetryTemplate
├── SqlExceptionClassifier
├── MySqlMachineIdRepository
├── MySqlSegmentRepository
├── TransactionTemplate
└── SchemaInitializer
```

MySQL 首期 DDL 建议：

```sql
create table id_machine (
  namespace varchar(64) not null,
  machine_id int unsigned not null,
  instance_id varchar(128) not null,
  status varchar(16) not null,
  last_timestamp bigint unsigned not null default 0,
  last_heartbeat_at datetime(3) not null,
  lease_expires_at datetime(3) not null,
  version bigint unsigned not null default 0,
  created_at datetime(3) not null,
  updated_at datetime(3) not null,
  primary key (namespace, machine_id),
  unique key uk_machine_instance (namespace, instance_id),
  key idx_machine_reclaim (namespace, status, lease_expires_at),
  key idx_machine_heartbeat (namespace, last_heartbeat_at)
) engine=InnoDB;

create table id_segment (
  namespace varchar(64) not null,
  tag varchar(64) not null,
  max_id bigint unsigned not null default 0,
  step bigint unsigned not null,
  version bigint unsigned not null default 0,
  last_fetch_at datetime(3) default null,
  updated_at datetime(3) not null,
  created_at datetime(3) not null,
  primary key (namespace, tag)
) engine=InnoDB;
```

索引意义：

- `pk(namespace, machine_id)`：保证同 namespace 内机器号唯一，并支持按机器号抢占。
- `uk_machine_instance(namespace, instance_id)`：支持实例重启幂等复用。
- `idx_machine_reclaim(namespace, status, lease_expires_at)`：支持查找 `RELEASED` 或租约过期记录。
- `idx_machine_heartbeat(namespace, last_heartbeat_at)`：支持运维巡检和指标查询。
- `pk(namespace, tag)`：保证每个业务 tag 只有一条号段游标。

### xiaohashu-id-generator-spring-boot-starter

职责：配置、Bean 创建、生命周期、health、Micrometer 绑定。

配置建议：

```yaml
distributed-id:
  namespace: xiaohashu
  snowflake:
    enabled: true
    epoch: 2025-01-01T00:00:00Z
    timestamp-bits: 41
    machine-bits: 10
    sequence-bits: 12
    clock-backwards:
      spin-threshold: 1ms
      max-wait: 500ms
  machine:
    allocator: jdbc
    instance-id:
    stable: false
    heartbeat-interval: 10s
    lease-timeout: 30s
    max-heartbeat-failures: 2
  segment:
    enabled: true
    default-step: 10000
  segment-chain:
    enabled: true
    safe-distance: 2
    max-prefetch-distance: 1024
    prefetch-period: 1s
    prefetch-retry-count: 3
    prefetch-retry-backoff: 50ms
```

校验规则：

- `timestamp-bits + machine-bits + sequence-bits <= 63`。
- `machine-bits > 0`，`sequence-bits > 0`，`timestamp-bits > 0`。
- `epoch < now`，且 `now - epoch <= maxTimestamp`。
- `lease-timeout > heartbeat-interval * (max-heartbeat-failures + 1)`。
- `safe-distance > 0`，`max-prefetch-distance >= safe-distance`。
- `default-step > 0` 且不超过配置的最大步长。

### xiaohashu-id-generator-benchmark

职责：JMH benchmark 工程，只提供可执行压测，不伪造结果。

Benchmark 场景：

- `SnowflakeIdGenerator.nextId()`
- `SegmentIdGenerator.nextId()`，使用内存 allocator 与 MySQL allocator 分别测。
- `SegmentChainIdGenerator.nextId()`，覆盖不同 `step`、`safeDistance`、DB 延迟模拟。

## 迁移分阶段建议

1. Phase 2：先创建 `core` 模块和 `ARCHITECTURE.md`，只定义接口、配置对象、异常、测试时钟，不连 DB。
2. Phase 3：实现 Snowflake + lease-aware `MachineLeaseMonitor`，再接入 `JdbcMachineIdAllocator`。
3. Phase 4：实现 `SegmentIdGenerator` + `JdbcSegmentAllocator`，先保证事务、并发和边界测试。
4. Phase 5：实现 `SegmentChainIdGenerator` 和共享 `SegmentPrefetchScheduler`，重点压并发切换和 prefetch race。
5. Phase 6：Spring Boot Starter，配置 validation、Bean 注册和 graceful shutdown。
6. Phase 7：`schema/mysql.sql` 与 Testcontainers MySQL 集成测试。
7. Phase 8：Micrometer metrics、health indicator、lease lost 与 hunger 暴露。
8. Phase 9：JMH benchmark 工程。
9. Phase 10：并发、正确性、失败模式、SQL、线程泄漏、License review。

## 生产故障行为原则

- Snowflake：启动拿不到 machine lease 时拒绝启动；运行期 DB 短暂不可用但本地 lease 未过安全窗口时可继续；超过安全窗口或 heartbeat 明确发现 lease lost 后拒绝生成。
- SegmentId：当前号段未耗尽时继续；耗尽且 DB 不可用时失败。
- SegmentChainId：链上剩余段可继续；链耗尽且 DB 不可用时失败；预取失败只影响后续缓冲，不影响已有 ID。
- 所有场景优先保证“不重复 ID”，宁可短暂不可用。

## License 结论

本阶段只阅读和分析 CosId，不复制源码。后续实现若直接复制或实质性改写 CosId 类，需要在对应源码头部保留 Apache License 2.0 版权声明，并在 `THIRD_PARTY_NOTICES.md` 明确：

- copied：逐文件列出直接复制文件。
- modified：逐文件列出改写来源。
- inspired/reimplemented：列出仅参考设计后自研实现的模块。

当前建议采用 inspired/reimplemented 路线：保留算法思想和并发语义，重写包结构、配置、JDBC 状态模型、重试和生命周期。
