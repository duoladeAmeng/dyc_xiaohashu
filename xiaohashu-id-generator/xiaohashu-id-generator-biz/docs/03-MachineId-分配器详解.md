# MachineId 分配器详解

## 一、整体思路

### 1.1 为什么需要 MachineId

Snowflake 算法中，机器 ID 是保证全局唯一的关键：

- **时间戳**：同一毫秒内相同
- **序列号**：0-4095 循环
- **机器 ID**：不同机器不同

如果两台机器分配了相同的 machineId，就会产生重复 ID。

### 1.2 核心设计目标

1. **全局唯一**：不同实例分配不同的 machineId
2. **高可用**：实例重启后能复用已分配的 machineId
3. **自动回收**：实例宕机后，machineId 可被其他实例复用
4. **保护机制**：防止正在使用的 machineId 被回收

### 1.3 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          GuardDistribute (入口)                              │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                     MachineIdAllocator                                │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │  distribute(namespace, machineBit, instanceId, safeGuardDuration) │  │  │
│  │  │                                                                   │  │  │
│  │  │  1. 检查本地存储 (MachineStateStorage)                            │  │  │
│  │  │     └── 存在 → syncUninterruptibly() → 返回                      │  │  │
│  │  │                                                                   │  │  │
│  │  │  2. 远程分配 (distributeRemote)                                   │  │  │
│  │  │     ├── 2.1 distributeBySelf (复用自己的)                         │  │  │
│  │  │     ├── 2.2 distributeByRevert (复用可回收的)                     │  │  │
│  │  │     └── 2.3 distributeMachine (分配新的)                          │  │  │
│  │  │                                                                   │  │  │
│  │  │  3. 保存到本地存储                                                │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │  guard(namespace, instanceId, safeGuardDuration)                │  │  │
│  │  │  - 定期心跳，防止 machineId 被回收                               │  │  │
│  │  │  - UPDATE last_timestamp                                         │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │  revert(namespace, instanceId)                                  │  │  │
│  │  │  - 实例关闭时调用                                                │  │  │
│  │  │  - stable: 保留 instance_id                                      │  │  │
│  │  │  - unstable: 清空 instance_id                                    │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  MachineIdGuarder (定时任务)                                          │  │
│  │  - 定期调用 guard() 保持 machineId 活跃                               │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、具体实现

### 2.1 MachineIdAllocator 接口

**文件位置**：`core/machine/MachineIdAllocator.java`

```java
public interface MachineIdAllocator {
    
    // 永久保护时长
    Duration FOREVER_SAFE_GUARD_DURATION = Duration.ofMillis(Long.MAX_VALUE);
    
    // 分配 machineId
    MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration);
    
    // 回收 machineId
    void revert(String namespace, InstanceId instanceId);
    
    // 保护 machineId (心跳)
    void guard(String namespace, InstanceId instanceId, Duration safeGuardDuration);
    
    // 计算最大 machineId
    static int maxMachineId(int machineBit) {
        if (machineBit <= 0 || machineBit >= Integer.SIZE) {
            throw new IllegalArgumentException("machineBit must be between 1 and 31.");
        }
        return ~(-1 << machineBit);
    }
    
    // 计算 machineId 总数
    static int totalMachineIds(int machineBit) {
        return maxMachineId(machineBit) + 1;
    }
    
    // 生成 namespace:machineId 格式
    static String namespacedMachineId(String namespace, int machineId) {
        return namespace + "." + String.format("%08d", machineId);
    }
    
    // 计算保护时间点
    static long getSafeGuardAt(Duration safeGuardDuration, boolean stable) {
        if (stable) {
            return 0L;  // stable 实例永久保护
        }
        if (FOREVER_SAFE_GUARD_DURATION.equals(safeGuardDuration)) {
            return 0L;
        }
        long safeGuardAt = System.currentTimeMillis() - safeGuardDuration.toMillis();
        if (safeGuardAt < 0) {
            return 0L;
        }
        return safeGuardAt;
    }
}
```

**关键点**：

1. **namespacedMachineId**：生成 `namespace.machineId` 格式，用于数据库 name 字段
2. **getSafeGuardAt**：计算保护时间点，用于判断 machineId 是否可被回收

### 2.2 AbstractMachineIdAllocator 类

**文件位置**：`core/machine/AbstractMachineIdAllocator.java`

#### 2.2.1 distribute() 模板方法

```java
@Override
public MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
    // 1. 参数校验
    if (namespace == null || namespace.isEmpty()) {
        throw new IllegalArgumentException("namespace can not be empty!");
    }
    if (machineBit <= 0) {
        throw new IllegalArgumentException(String.format("machineBit:[%s] must be greater than 0!", machineBit));
    }
    if (instanceId == null) {
        throw new NullPointerException("instanceId can not be null!");
    }

    // 2. 检查本地存储
    MachineState localState = machineStateStorage.get(namespace, instanceId);
    if (!MachineState.NOT_FOUND.equals(localState)) {
        // 2.1 本地存在
        ensureMachineId(machineBit, instanceId, localState);
        clockBackwardsSynchronizer.syncUninterruptibly(localState.getLastTimeStamp());
        return localState;
    }

    // 3. 远程分配
    localState = distributeRemote(namespace, machineBit, instanceId, safeGuardDuration);
    ensureMachineId(machineBit, instanceId, localState);
    
    // 4. 时钟回拨处理
    if (ClockBackwardsSynchronizer.getBackwardsTimeStamp(localState.getLastTimeStamp()) > 0) {
        clockBackwardsSynchronizer.syncUninterruptibly(localState.getLastTimeStamp());
        localState = MachineState.of(localState.getMachineId(), System.currentTimeMillis());
    }

    // 5. 保存到本地存储
    machineStateStorage.set(namespace, localState.getMachineId(), instanceId);
    return localState;
}
```

**流程图**：
```
调用 distribute()
    │
    ├── 1. 参数校验
    │
    ├── 2. 检查本地存储
    │   ├── 存在 → ensureMachineId() → syncUninterruptibly() → 返回
    │   └── 不存在 → 继续
    │
    ├── 3. 远程分配 (distributeRemote)
    │   ├── distributeBySelf
    │   ├── distributeByRevert
    │   └── distributeMachine
    │
    ├── 4. ensureMachineId() (校验范围)
    │
    ├── 5. 时钟回拨处理
    │   └── 如果 lastTimestamp > currentTime → syncUninterruptibly()
    │
    └── 6. 保存到本地存储
```

#### 2.2.2 revert() 方法

```java
@Override
public void revert(String namespace, InstanceId instanceId) {
    // 1. 获取本地状态
    MachineState lastLocalState = resetStorage(namespace, instanceId);
    
    // 2. 远程回收
    revertRemote(namespace, instanceId, lastLocalState);
    
    // 3. 移除本地存储
    machineStateStorage.remove(namespace, instanceId);
}
```

#### 2.2.3 guard() 方法

```java
@Override
public void guard(String namespace, InstanceId instanceId, Duration safeGuardDuration) throws NotFoundMachineStateException, MachineIdLostException {
    // 1. 获取本地状态
    MachineState lastLocalState = resetStorage(namespace, instanceId);
    
    // 2. 远程保护 (心跳)
    guardRemote(namespace, instanceId, lastLocalState, safeGuardDuration);
}
```

#### 2.2.4 resetStorage() 方法

```java
private MachineState resetStorage(String namespace, InstanceId instanceId) {
    // 1. 参数校验
    if (namespace == null || namespace.isEmpty()) {
        throw new IllegalArgumentException("namespace can not be empty!");
    }
    if (instanceId == null) {
        throw new NullPointerException("instanceId can not be null!");
    }

    // 2. 获取本地状态
    MachineState lastLocalState = machineStateStorage.get(namespace, instanceId);
    if (MachineState.NOT_FOUND.equals(lastLocalState)) {
        throw new NotFoundMachineStateException(namespace, instanceId);
    }
    
    // 3. 时钟回拨处理
    if (getBackwardsTimeStamp(lastLocalState.getLastTimeStamp()) < 0) {
        // lastTimestamp > currentTime，说明时钟回拨
        lastLocalState = MachineState.of(lastLocalState.getMachineId(), System.currentTimeMillis());
        machineStateStorage.set(namespace, lastLocalState.getMachineId(), instanceId);
    }
    return lastLocalState;
}
```

### 2.3 JdbcMachineIdAllocator 类

**文件位置**：`jdbc/JdbcMachineIdAllocator.java`

#### 2.3.1 SQL 语句

```java
// 查询自己的 machineId
public static final String GET_MACHINE_STATE = 
    "select machine_id, last_timestamp from cosid_machine where namespace=? and instance_id=? and last_timestamp>?";

// 查询可回收的 machineId
public static final String GET_REVERT_MACHINE_STATE = 
    "select machine_id, last_timestamp from cosid_machine where namespace=? and (instance_id='' or last_timestamp<=?)";

// 更新可回收的 machineId
public static final String DISTRIBUTE_REVERT_MACHINE_STATE = 
    "update cosid_machine set instance_id=?,last_timestamp=?,distribute_time=? where name=? and (instance_id='' or last_timestamp<=?)";

// 获取下一个 machineId
public static final String NEXT_MACHINE_ID = 
    "select max(machine_id)+1 as next_machine_id from cosid_machine where namespace=?";

// 插入新的 machineId
public static final String DISTRIBUTE_MACHINE = 
    "insert into cosid_machine (name, namespace, machine_id, last_timestamp, instance_id, distribute_time, revert_time) values (?,?,?,?,?,?,0);";

// 回收 machineId
public static final String REVERT_MACHINE_STATE = 
    "update cosid_machine set instance_id=?,last_timestamp=?,revert_time=? where namespace=? and instance_id=?";

// 保护 machineId (心跳)
public static final String GUARD_MACHINE_STATE = 
    "update cosid_machine set last_timestamp=? where namespace=? and instance_id=? and machine_id=?";
```

#### 2.3.2 distributeRemote() 方法

```java
@Override
protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
    try (Connection connection = dataSource.getConnection()) {
        // 1. 尝试复用自己的
        MachineState selfMachineState = distributeBySelf(connection, namespace, instanceId, safeGuardDuration);
        if (!MachineState.NOT_FOUND.equals(selfMachineState)) {
            return selfMachineState;
        }
        
        // 2. 尝试复用可回收的
        MachineState revertMachineState = distributeByRevert(connection, namespace, instanceId, safeGuardDuration);
        if (!MachineState.NOT_FOUND.equals(revertMachineState)) {
            return revertMachineState;
        }
        
        // 3. 分配新的
        return distributeMachine(connection, namespace, machineBit, instanceId);
    } catch (SQLException sqlException) {
        log.error("Distribute MachineId Failed.", sqlException);
        throw new IdGeneratorException(sqlException);
    }
}
```

**流程图**：
```
distributeRemote()
    │
    ├── 1. distributeBySelf
    │   └── 查询: instance_id = 当前实例 且 last_timestamp > safeGuardAt
    │       ├── 找到 → guardRemote() → 返回
    │       └── 未找到 → 继续
    │
    ├── 2. distributeByRevert
    │   └── 查询: instance_id = '' 或 last_timestamp <= safeGuardAt
    │       ├── 找到 → CAS 更新 → 返回
    │       └── 未找到 → 继续
    │
    └── 3. distributeMachine
        ├── MAX(machine_id) + 1
        ├── INSERT
        └── 如果 duplicate key → 重新分配
```

#### 2.3.3 distributeBySelf() 方法

```java
public MachineState distributeBySelf(Connection connection, String namespace, InstanceId instanceId, Duration safeGuardDuration) throws SQLException {
    // 1. 计算保护时间点
    long safeGuardAt = MachineIdAllocator.getSafeGuardAt(safeGuardDuration, instanceId.isStable());
    
    // 2. 查询自己的 machineId
    try (PreparedStatement statement = connection.prepareStatement(GET_MACHINE_STATE)) {
        statement.setString(1, namespace);
        statement.setString(2, instanceId.getInstanceId());
        statement.setLong(3, safeGuardAt);
        
        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                // 3. 找到，返回
                MachineState machineState = MachineState.of(
                    resultSet.getInt(1), 
                    Math.max(resultSet.getLong(2), System.currentTimeMillis())  // 保护 high-water mark
                );
                guardRemote(namespace, instanceId, machineState, safeGuardDuration);
                return machineState;
            }
        }
    }
    return MachineState.NOT_FOUND;
}
```

**关键点**：
- `Math.max(resultSet.getLong(2), System.currentTimeMillis())`：保护 high-water mark，防止时钟回拨导致降低

#### 2.3.4 distributeByRevert() 方法

```java
public MachineState distributeByRevert(Connection connection, String namespace, InstanceId instanceId, Duration safeGuardDuration) throws SQLException {
    long safeGuardAt = MachineIdAllocator.getSafeGuardAt(safeGuardDuration, instanceId.isStable());
    
    // 1. 查询可回收的 machineId
    try (PreparedStatement statement = connection.prepareStatement(GET_REVERT_MACHINE_STATE)) {
        statement.setString(1, namespace);
        statement.setLong(2, safeGuardAt);
        
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                MachineState machineState = MachineState.of(resultSet.getInt(1), resultSet.getLong(2));
                
                // 2. CAS 更新
                int row = distributeRevertMachineState(connection, namespace, instanceId, machineState, safeGuardAt);
                if (row > 0) {
                    return machineState;
                }
            }
        }
    }
    return MachineState.NOT_FOUND;
}
```

#### 2.3.5 distributeRevertMachineState() 方法

```java
public int distributeRevertMachineState(Connection connection, String namespace, InstanceId instanceId, MachineState machineState, long safeGuardAt) throws SQLException {
    long now = System.currentTimeMillis();
    
    // CAS 更新：只有 instance_id='' 或 last_timestamp<=safeGuardAt 时才能更新
    try (PreparedStatement statement = connection.prepareStatement(DISTRIBUTE_REVERT_MACHINE_STATE)) {
        statement.setString(1, instanceId.getInstanceId());
        statement.setLong(2, now);
        statement.setLong(3, now);
        statement.setString(4, MachineIdAllocator.namespacedMachineId(namespace, machineState.getMachineId()));
        statement.setLong(5, safeGuardAt);
        return statement.executeUpdate();
    }
}
```

**CAS 语义**：
- `WHERE name=? AND (instance_id='' OR last_timestamp<=?)`
- 只有当 instance_id 为空或 last_timestamp 过期时才能更新
- 多个实例同时抢同一个 machineId 时，只有一个能成功

#### 2.3.6 distributeMachine() 方法

```java
public MachineState distributeMachine(Connection connection, String namespace, int machineBit, InstanceId instanceId) throws SQLException {
    // 1. 获取下一个 machineId
    int nextMachineId = nextMachineId(connection, namespace);
    
    // 2. 检查是否溢出
    if (nextMachineId > MachineIdAllocator.maxMachineId(machineBit)) {
        throw new MachineIdOverflowException(MachineIdAllocator.totalMachineIds(machineBit), instanceId);
    }
    
    // 3. 插入新的
    MachineState machineState = MachineState.of(nextMachineId, System.currentTimeMillis());
    try {
        distributeMachine(connection, namespace, instanceId, machineState);
    } catch (SQLIntegrityConstraintViolationException sqlIntegrityConstraintViolationException) {
        // 4. duplicate key，重新分配
        log.info("MachineId Duplicate.", sqlIntegrityConstraintViolationException);
        return distributeMachine(connection, namespace, machineBit, instanceId);
    }
    return machineState;
}
```

**流程图**：
```
distributeMachine()
    │
    ├── 1. MAX(machine_id) + 1
    │
    ├── 2. 检查溢出
    │   └── 溢出 → 抛异常
    │
    ├── 3. INSERT
    │   ├── 成功 → 返回
    │   └── duplicate key → 重新分配
    │
    └── 4. 返回 MachineState
```

#### 2.3.7 guardRemote() 方法

```java
@Override
protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(GUARD_MACHINE_STATE)) {
        
        // 使用 machineState.getLastTimeStamp()，保护 high-water mark
        statement.setLong(1, machineState.getLastTimeStamp());
        statement.setString(2, namespace);
        statement.setString(3, instanceId.getInstanceId());
        statement.setInt(4, machineState.getMachineId());
        
        if (statement.executeUpdate() == 0) {
            // 影响行数为 0，说明 machineId 已丢失
            throw new MachineIdLostException(namespace, instanceId, machineState);
        }
    } catch (SQLException sqlException) {
        throw new IdGeneratorException(sqlException);
    }
}
```

**关键点**：
- `statement.setLong(1, machineState.getLastTimeStamp())`：使用 high-water mark，不是 System.currentTimeMillis()
- 这样可以防止时钟回拨导致降低 last_timestamp

#### 2.3.8 revertRemote() 方法

```java
@Override
protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(REVERT_MACHINE_STATE)) {
        
        // stable: 保留 instance_id
        // unstable: 清空 instance_id
        statement.setString(1, instanceId.isStable() ? instanceId.getInstanceId() : "");
        statement.setLong(2, machineState.getLastTimeStamp());
        statement.setLong(3, System.currentTimeMillis());
        statement.setString(4, namespace);
        statement.setString(5, instanceId.getInstanceId());
        
        statement.executeUpdate();
    } catch (SQLException sqlException) {
        throw new IdGeneratorException(sqlException);
    }
}
```

**stable vs unstable**：
- **stable**：实例关闭后，保留 instance_id，其他实例不能普通回收，原实例重启可重新得到原 machineId
- **unstable**：实例关闭后，清空 instance_id，machineId 可重新分配

### 2.4 MachineState 类

**文件位置**：`core/machine/MachineState.java`

```java
public class MachineState {
    
    public static final MachineState NOT_FOUND = new MachineState(-1, -1);
    
    private final int machineId;
    private final long lastTimeStamp;
    
    public static MachineState of(int machineId, long lastTimeStamp) {
        return new MachineState(machineId, lastTimeStamp);
    }
    
    // getters...
}
```

### 2.5 MachineStateStorage 接口

**文件位置**：`core/machine/MachineStateStorage.java`

```java
public interface MachineStateStorage {
    
    MachineStateStorage LOCAL = new LocalMachineStateStorage();
    MachineStateStorage IN_MEMORY = new InMemoryMachineStateStorage();
    
    MachineState get(String namespace, InstanceId instanceId);
    
    void set(String namespace, int machineId, InstanceId instanceId);
    
    void remove(String namespace, InstanceId instanceId);
}
```

### 2.6 LocalMachineStateStorage 类

**文件位置**：`core/machine/LocalMachineStateStorage.java`

```java
public class LocalMachineStateStorage implements MachineStateStorage {
    
    // 使用 ConcurrentHashMap 存储
    private final ConcurrentHashMap<NamespacedInstanceId, MachineState> storage = new ConcurrentHashMap<>();
    
    @Override
    public MachineState get(String namespace, InstanceId instanceId) {
        return storage.getOrDefault(new NamespacedInstanceId(namespace, instanceId), MachineState.NOT_FOUND);
    }
    
    @Override
    public void set(String namespace, int machineId, InstanceId instanceId) {
        storage.put(new NamespacedInstanceId(namespace, instanceId), MachineState.of(machineId, System.currentTimeMillis()));
    }
    
    @Override
    public void remove(String namespace, InstanceId instanceId) {
        storage.remove(new NamespacedInstanceId(namespace, instanceId));
    }
}
```

### 2.7 InstanceId 类

**文件位置**：`core/machine/InstanceId.java`

```java
public class InstanceId {
    
    private final String instanceId;
    private final boolean stable;
    
    public static InstanceId of(String instanceId, boolean stable) {
        return new InstanceId(instanceId, stable);
    }
    
    // getters...
}
```

**instanceId 生成规则**：
```java
private String resolveInstanceId() {
    if (properties.getInstanceId() != null && !properties.getInstanceId().isBlank()) {
        return properties.getInstanceId();
    }
    try {
        // IP:PID 格式
        return InetAddress.getLocalHost().getHostAddress() + ":" + ManagementFactory.getRuntimeMXBean().getName();
    } catch (UnknownHostException ignored) {
        return UUID.randomUUID().toString();
    }
}
```

### 2.8 GuardDistribute 类

**文件位置**：`core/machine/GuardDistribute.java`

```java
public class GuardDistribute implements MachineIdDistribute {
    
    private final MachineIdAllocator machineIdAllocator;
    private final MachineIdGuarder machineIdGuarder;
    
    @Override
    public MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) throws MachineIdOverflowException {
        // 1. 分配 machineId
        MachineState machineState = machineIdAllocator.distribute(namespace, machineBit, instanceId, safeGuardDuration);
        
        // 2. 注册到 guarder (开始心跳)
        machineIdGuarder.register(namespace, instanceId);
        
        return machineState;
    }
}
```

### 2.9 DefaultMachineIdGuarder 类

**文件位置**：`core/machine/DefaultMachineIdGuarder.java`

#### 2.9.1 定时任务

```java
@Override
public void start() {
    if (running.compareAndSet(false, true)) {
        // 定时调用 safeGuard()
        scheduledFuture = executorService.scheduleWithFixedDelay(
            this::safeGuard, 
            initialDelay.toMillis(), 
            delay.toMillis(), 
            TimeUnit.MILLISECONDS
        );
    }
}
```

#### 2.9.2 safeGuard() 方法

```java
void safeGuard() {
    for (NamespacedInstanceId registeredInstance : guardianStates.keySet()) {
        long guardAt = System.currentTimeMillis();
        try {
            // 调用 guard() 心跳
            machineIdAllocator.guard(
                registeredInstance.getNamespace(), 
                registeredInstance.getInstanceId(), 
                safeGuardDuration
            );
            guardianStates.put(registeredInstance, GuardianState.success(guardAt));
        } catch (Throwable throwable) {
            guardianStates.put(registeredInstance, GuardianState.failed(guardAt, throwable));
            log.error("Guard Failed:[{}]!", throwable.getMessage(), throwable);
        }
    }
}
```

---

## 三、数据库设计

### 3.1 cosid_machine 表

```sql
CREATE TABLE cosid_machine (
    name VARCHAR(128) NOT NULL,           -- namespace:machine_id (主键)
    namespace VARCHAR(128) NOT NULL,      -- 命名空间
    machine_id INT NOT NULL,              -- 机器 ID
    instance_id VARCHAR(128) NOT NULL,    -- 实例 ID
    last_timestamp BIGINT NOT NULL,       -- 最后时间戳 (high-water mark)
    distribute_time BIGINT NOT NULL,      -- 分配时间
    revert_time BIGINT NOT NULL,          -- 回收时间
    PRIMARY KEY (namespace, machine_id)
);
```

### 3.2 索引

```sql
-- 查询自己的 machineId
CREATE INDEX idx_instance ON cosid_machine(namespace, instance_id, last_timestamp);

-- 查询可回收的 machineId
CREATE INDEX idx_revert ON cosid_machine(namespace, instance_id, last_timestamp);
```

---

## 四、场景推演

### 4.1 场景 1：首次分配

```
实例 A 启动：
  1. 检查本地存储 → 未找到
  2. distributeBySelf → 未找到
  3. distributeByRevert → 未找到
  4. distributeMachine
     ├── MAX(machine_id) + 1 = 0
     ├── INSERT (machine_id=0)
     └── 返回 MachineState(0, currentTime)
  5. 保存到本地存储
  6. 注册到 guarder
```

### 4.2 场景 2：重启后复用

```
实例 A 重启：
  1. 检查本地存储 → 未找到 (重启后清空)
  2. distributeBySelf
     └── 查询: instance_id='A' AND last_timestamp > safeGuardAt
         └── 找到 (因为是 stable 实例)
  3. guardRemote() (心跳)
  4. 返回原来的 MachineState
```

### 4.3 场景 3：复用可回收的

```
实例 A 宕机：
  - instance_id = '' (unstable) 或 last_timestamp <= safeGuardAt

实例 B 启动：
  1. 检查本地存储 → 未找到
  2. distributeBySelf → 未找到
  3. distributeByRevert
     └── 查询: instance_id='' OR last_timestamp<=safeGuardAt
         └── 找到实例 A 的 machineId
  4. CAS 更新
     └── UPDATE ... WHERE name=? AND (instance_id='' OR last_timestamp<=?)
         └── 成功 (只有实例 B 能成功)
  5. 返回 MachineState
```

### 4.4 场景 4：同时抢同一个 machineId

```
实例 A 和 B 同时启动，都想复用 machineId=0：

实例 A：
  1. distributeByRevert → 找到 machineId=0
  2. CAS UPDATE ... WHERE name='xiaohashu.00000000' AND (instance_id='' OR last_timestamp<=?)
     └── 成功

实例 B：
  1. distributeByRevert → 找到 machineId=0
  2. CAS UPDATE ... WHERE name='xiaohashu.00000000' AND (instance_id='' OR last_timestamp<=?)
     └── 失败 (因为 instance_id 已经被 A 设置)
  3. 继续查找下一个可回收的
```

### 4.5 场景 5：heartbeat 失败

```
实例 A 正在使用 machineId=0：
  - 定期调用 guardRemote()
  - UPDATE cosid_machine SET last_timestamp=? WHERE namespace=? AND instance_id=? AND machine_id=?

某次 guard：
  - UPDATE 影响行数 = 0
  - 说明 machineId 已被回收 (可能是 DBA 手动删除)
  - 抛出 MachineIdLostException
```

### 4.6 场景 6：时钟回拨期间 guard

```
当前时间：1700000000000
lastTimestamp：1700000001000 (high-water mark)

调用 guardRemote()：
  - UPDATE SET last_timestamp=1700000001000  ← 使用 high-water mark
  - 不是 UPDATE SET last_timestamp=1700000000000  ← 不使用 currentTime

原因：保护 high-water mark，防止时钟回拨导致降低
```

---

## 五、关键设计决策

### 5.1 为什么需要 safeGuardDuration？

- 防止正在使用的 machineId 被回收
- 例如：safeGuardDuration=5分钟，只有 last_timestamp 超过 5 分钟未更新的才能被回收

### 5.2 为什么需要 stable/unstable？

- **stable**：实例关闭后保留 instance_id，适合长期运行的实例
- **unstable**：实例关闭后清空 instance_id，适合短期运行的实例 (如 Serverless)

### 5.3 为什么 guard 使用 high-water mark？

- 防止时钟回拨导致降低 last_timestamp
- 例如：已发号到 T=10000，时钟回拨到 T=9500
- 如果使用 currentTime (9500)，会导致 high-water mark 降低
- 使用 lastTimestamp (10000)，保护 high-water mark

### 5.4 为什么 distributeBySelf 要保护 high-water mark？

```java
MachineState machineState = MachineState.of(
    resultSet.getInt(1), 
    Math.max(resultSet.getLong(2), System.currentTimeMillis())  // 保护 high-water mark
);
```

- 防止时钟回拨导致降低 last_timestamp
- 确保 high-water mark 只增不减

---

## 六、与 CosId 的对比

### 6.1 SQL 语句

**完全一致**。所有 SQL 语句与 CosId 相同。

### 6.2 distribute 流程

**完全一致**。都是：distributeBySelf → distributeByRevert → distributeMachine

### 6.3 guard 使用 high-water mark

**完全一致**。都是使用 `machineState.getLastTimeStamp()`，不是 `System.currentTimeMillis()`

### 6.4 stable/unstable 语义

**完全一致**。都是 stable 保留 instance_id，unstable 清空 instance_id

---

## 七、总结

MachineId 分配器的核心要点：

1. **三层分配策略**：复用自己的 → 复用可回收的 → 分配新的
2. **CAS 语义**：防止多个实例同时抢同一个 machineId
3. **High-water mark 保护**：防止时钟回拨导致降低
4. **定时心跳**：防止正在使用的 machineId 被回收
5. **stable/unstable**：适应不同场景的实例生命周期

通过这些机制，确保了分布式环境下 machineId 的唯一性和高可用性。
