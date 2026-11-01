# Spring Boot 集成与 API 详解

## 一、整体思路

### 1.1 设计目标

1. **开箱即用**：通过 Spring Boot 自动配置，快速集成
2. **配置灵活**：支持多种配置方式
3. **生命周期管理**：自动管理 MachineId 的分配和回收
4. **API 友好**：提供简洁的 REST API

### 1.2 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Spring Boot 应用                                   │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  @Configuration                                                       │  │
│  │  IdGeneratorConfiguration                                            │  │
│  │                                                                       │  │
│  │  @PostConstruct validate()                                            │  │
│  │                                                                       │  │
│  │  @Bean                                                                │  │
│  │  ├── DefaultClockBackwardsSynchronizer                                │  │
│  │  ├── JdbcMachineIdAllocator                                           │  │
│  │  ├── MachineIdGuarder                                                 │  │
│  │  ├── GuardDistribute                                                  │  │
│  │  ├── CosIdMachineIdLifecycle                                          │  │
│  │  ├── segmentJdbcAllocator                                             │  │
│  │  ├── segmentChainJdbcAllocator                                        │  │
│  │  ├── PrefetchWorkerExecutorService                                    │  │
│  │  ├── snowflakeIdGenerator                                             │  │
│  │  ├── segmentIdGenerator                                               │  │
│  │  ├── segmentChainIdGenerator                                          │  │
│  │  └── jdbcIdGeneratorSchemaInitializer                                 │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  @Controller                                                          │  │
│  │  DistributedIdController                                              │  │
│  │                                                                       │  │
│  │  POST /api/next-id                                                    │  │
│  │  POST /api/batch-next-ids                                             │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  @Service                                                             │  │
│  │  DistributedIdGenerateServiceImpl                                     │  │
│  │                                                                       │  │
│  │  nextId(type)                                                         │  │
│  │  batchGenerateIds(type, size)                                         │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  @ConfigurationProperties                                             │  │
│  │  DistributedIdProperties                                              │  │
│  │                                                                       │  │
│  │  namespace                                                            │  │
│  │  instanceId                                                           │  │
│  │  stableInstance                                                       │  │
│  │  snowflake.*                                                          │  │
│  │  segment.*                                                            │  │
│  │  segment-chain.*                                                      │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、配置属性详解

### 2.1 DistributedIdProperties 类

**文件位置**：`config/DistributedIdProperties.java`

#### 2.1.1 核心字段

```java
@Validated
@ConfigurationProperties(prefix = "distributed-id")
public class DistributedIdProperties {
    
    @NotBlank
    private String namespace = "xiaohashu";  // 命名空间
    
    private String instanceId;               // 实例 ID (可选)
    
    private boolean stableInstance = false;  // 是否稳定实例
    
    @Valid
    private Jdbc jdbc = new Jdbc();          // JDBC 配置
    
    @Valid
    private Snowflake snowflake = new Snowflake();  // Snowflake 配置
    
    @Valid
    private Segment segment = new Segment();  // Segment 配置
    
    @Valid
    private SegmentChain segmentChain = new SegmentChain();  // SegmentChain 配置
}
```

#### 2.1.2 validate() 方法

```java
public void validate() {
    // 1. 校验位数总和
    int totalBit = snowflake.timestampBit + snowflake.machineBit + snowflake.sequenceBit;
    if (totalBit > 63) {
        throw new IllegalArgumentException("snowflake timestampBit + machineBit + sequenceBit must be <= 63.");
    }
    
    // 2. 校验 epoch
    if (!snowflake.epoch.isBefore(Instant.now().plus(Duration.ofDays(1)))) {
        throw new IllegalArgumentException("snowflake epoch is unreasonable.");
    }
}
```

### 2.2 Snowflake 配置

```java
public static class Snowflake {
    private boolean enabled = true;                                        // 是否启用
    private Instant epoch = Instant.ofEpochMilli(CosId.COSID_EPOCH);      // 起始时间戳
    @Min(1)
    private int timestampBit = SnowflakeIdGenerator.DEFAULT_TIMESTAMP_BIT;  // 时间戳位数
    @Min(1)
    private int machineBit = SnowflakeIdGenerator.DEFAULT_MACHINE_BIT;      // 机器 ID 位数
    @Min(1)
    private int sequenceBit = SnowflakeIdGenerator.DEFAULT_SEQUENCE_BIT;    // 序列号位数
    private long sequenceResetThreshold = SnowflakeIdGenerator.DEFAULT_SEQUENCE_RESET_THRESHOLD;  // 序列号重置阈值
    private boolean clockSync = true;                                      // 是否开启时钟同步
    private Duration guarderInitialDelay = DefaultMachineIdGuarder.DEFAULT_INITIAL_DELAY;  // Guarder 初始延迟
    private Duration guarderDelay = DefaultMachineIdGuarder.DEFAULT_DELAY;  // Guarder 延迟
    private Duration safeGuardDuration = Duration.ofMinutes(5);            // 保护时长
    @Min(1)
    private int clockSpinThreshold = DefaultClockBackwardsSynchronizer.DEFAULT_SPIN_THRESHOLD;  // 时钟回拨 spin 阈值
    @Min(2)
    private int clockBrokenThreshold = DefaultClockBackwardsSynchronizer.DEFAULT_BROKEN_THRESHOLD;  // 时钟回拨 broken 阈值
}
```

### 2.3 Segment 配置

```java
public static class Segment {
    private boolean enabled = true;      // 是否启用
    @NotBlank
    private String name = "segment";     // 号段名称
    @Min(1)
    private long step = 10000;           // 号段大小
    @Min(1)
    private long ttlSeconds = Long.MAX_VALUE;  // 号段有效期 (秒)
}
```

### 2.4 SegmentChain 配置

```java
public static class SegmentChain extends Segment {
    @Min(1)
    private int safeDistance = 2;                     // 安全距离
    private Duration prefetchPeriod = Duration.ofSeconds(1);  // 预取周期
    @Min(1)
    private int workerCorePoolSize = Runtime.getRuntime().availableProcessors();  // 工作线程数
    
    public SegmentChain() {
        setName("segment-chain");  // 默认名称
    }
}
```

### 2.5 Jdbc 配置

```java
public static class Jdbc {
    private boolean initializeSchema = true;  // 是否初始化 Schema
}
```

---

## 三、Bean 配置详解

### 3.1 IdGeneratorConfiguration 类

**文件位置**：`config/IdGeneratorConfiguration.java`

#### 3.1.1 validate() 方法

```java
@PostConstruct
public void validate() {
    properties.validate();
}
```

**作用**：在 Spring 初始化完成后，校验配置参数

#### 3.1.2 DefaultClockBackwardsSynchronizer Bean

```java
@Bean
@ConditionalOnMissingBean
public DefaultClockBackwardsSynchronizer defaultClockBackwardsSynchronizer() {
    DistributedIdProperties.Snowflake snowflake = properties.getSnowflake();
    return new DefaultClockBackwardsSynchronizer(
        snowflake.getClockSpinThreshold(), 
        snowflake.getClockBrokenThreshold()
    );
}
```

**作用**：创建时钟回拨同步器

#### 3.1.3 JdbcMachineIdAllocator Bean

```java
@Bean
@ConditionalOnMissingBean
public JdbcMachineIdAllocator jdbcMachineIdAllocator(DataSource dataSource, DefaultClockBackwardsSynchronizer clockBackwardsSynchronizer) {
    return new JdbcMachineIdAllocator(
        dataSource, 
        MachineStateStorage.LOCAL,  // 使用本地存储
        clockBackwardsSynchronizer
    );
}
```

**作用**：创建 JDBC MachineId 分配器

#### 3.1.4 MachineIdGuarder Bean

```java
@Bean
@ConditionalOnMissingBean
public MachineIdGuarder machineIdGuarder(JdbcMachineIdAllocator machineIdAllocator) {
    DistributedIdProperties.Snowflake snowflake = properties.getSnowflake();
    return new DefaultMachineIdGuarder(
        machineIdAllocator,
        DefaultMachineIdGuarder.executorService(),
        snowflake.getGuarderInitialDelay(),
        snowflake.getGuarderDelay(),
        snowflake.getSafeGuardDuration()
    );
}
```

**作用**：创建 MachineId 保护器 (定时心跳)

#### 3.1.5 GuardDistribute Bean

```java
@Bean
@ConditionalOnMissingBean
public GuardDistribute guardDistribute(JdbcMachineIdAllocator machineIdAllocator, MachineIdGuarder machineIdGuarder) {
    return new GuardDistribute(machineIdAllocator, machineIdGuarder);
}
```

**作用**：创建 Guard 分配器 (分配 + 注册心跳)

#### 3.1.6 CosIdMachineIdLifecycle Bean

```java
@Bean
@ConditionalOnMissingBean
public CosIdMachineIdLifecycle cosIdMachineIdLifecycle(MachineIdGuarder machineIdGuarder, JdbcMachineIdAllocator machineIdAllocator) {
    return new CosIdMachineIdLifecycle(machineIdGuarder, machineIdAllocator);
}
```

**作用**：创建生命周期管理器

#### 3.1.7 segmentJdbcAllocator Bean

```java
@Bean
@ConditionalOnMissingBean(name = "segmentJdbcAllocator")
@DependsOn("jdbcIdGeneratorSchemaInitializer")
public JdbcSegmentAllocator segmentJdbcAllocator(DataSource dataSource) {
    return new JdbcSegmentAllocator(
        properties.getNamespace(), 
        properties.getSegment().getName(), 
        properties.getSegment().getStep(), 
        dataSource
    );
}
```

**作用**：创建 Segment 的 JDBC 分配器

#### 3.1.8 segmentChainJdbcAllocator Bean

```java
@Bean
@ConditionalOnMissingBean(name = "segmentChainJdbcAllocator")
@DependsOn("jdbcIdGeneratorSchemaInitializer")
public JdbcSegmentAllocator segmentChainJdbcAllocator(DataSource dataSource) {
    return new JdbcSegmentAllocator(
        properties.getNamespace(), 
        properties.getSegmentChain().getName(), 
        properties.getSegmentChain().getStep(), 
        dataSource
    );
}
```

**作用**：创建 SegmentChain 的 JDBC 分配器

#### 3.1.9 PrefetchWorkerExecutorService Bean

```java
@Bean(destroyMethod = "shutdown")
@ConditionalOnMissingBean
public PrefetchWorkerExecutorService prefetchWorkerExecutorService() {
    return new PrefetchWorkerExecutorService(
        properties.getSegmentChain().getPrefetchPeriod(), 
        properties.getSegmentChain().getWorkerCorePoolSize()
    );
}
```

**作用**：创建预取工作线程池

#### 3.1.10 snowflakeIdGenerator Bean

```java
@Bean(name = "snowflakeIdGenerator")
@ConditionalOnMissingBean(name = "snowflakeIdGenerator")
@DependsOn("jdbcIdGeneratorSchemaInitializer")
public IdGenerator snowflakeIdGenerator(GuardDistribute guardDistribute, DefaultClockBackwardsSynchronizer clockBackwardsSynchronizer) {
    DistributedIdProperties.Snowflake snowflake = properties.getSnowflake();
    
    // 1. 生成 InstanceId
    InstanceId instanceId = InstanceId.of(resolveInstanceId(), properties.isStableInstance());
    
    // 2. 分配 MachineId
    MachineState machineState = guardDistribute.distribute(
        properties.getNamespace(), 
        snowflake.getMachineBit(), 
        instanceId, 
        snowflake.getSafeGuardDuration()
    );
    
    // 3. 创建 SnowflakeIdGenerator
    SnowflakeIdGenerator snowflakeIdGenerator = new SnowflakeIdGenerator(
        snowflake.getEpoch().toEpochMilli(),
        snowflake.getTimestampBit(),
        snowflake.getMachineBit(),
        snowflake.getSequenceBit(),
        machineState.getMachineId(),
        snowflake.getSequenceResetThreshold()
    );
    
    // 4. 如果开启时钟同步，包装成 ClockSyncSnowflakeIdGenerator
    if (snowflake.isClockSync()) {
        return new ClockSyncSnowflakeIdGenerator(snowflakeIdGenerator, clockBackwardsSynchronizer);
    }
    return snowflakeIdGenerator;
}
```

**关键点**：
- **@DependsOn**：确保 Schema 初始化完成后再创建
- **resolveInstanceId**：生成实例 ID (IP:PID 或 UUID)
- **分配 MachineId**：调用 guardDistribute.distribute()
- **时钟同步**：根据配置决定是否包装

#### 3.1.11 segmentIdGenerator Bean

```java
@Bean
@ConditionalOnMissingBean
public SegmentIdGenerator segmentIdGenerator(@Qualifier("segmentJdbcAllocator") JdbcSegmentAllocator segmentJdbcAllocator) {
    return new SegmentIdGenerator(properties.getSegment().getTtlSeconds(), segmentJdbcAllocator);
}
```

#### 3.1.12 segmentChainIdGenerator Bean

```java
@Bean
@ConditionalOnMissingBean
public SegmentChainIdGenerator segmentChainIdGenerator(@Qualifier("segmentChainJdbcAllocator") JdbcSegmentAllocator segmentChainJdbcAllocator,
                                                       PrefetchWorkerExecutorService prefetchWorkerExecutorService) {
    return new SegmentChainIdGenerator(
        properties.getSegmentChain().getTtlSeconds(),
        properties.getSegmentChain().getSafeDistance(),
        segmentChainJdbcAllocator,
        prefetchWorkerExecutorService
    );
}
```

#### 3.1.13 jdbcIdGeneratorSchemaInitializer Bean

```java
@Bean
public JdbcIdGeneratorSchemaInitializer jdbcIdGeneratorSchemaInitializer(DataSource dataSource) {
    JdbcIdGeneratorSchemaInitializer initializer = new JdbcIdGeneratorSchemaInitializer(dataSource);
    if (properties.getJdbc().isInitializeSchema()) {
        initializer.initialize(
            properties.getNamespace(),
            properties.getSegment().getName(),
            properties.getSegment().getStep(),
            properties.getSegmentChain().getName(),
            properties.getSegmentChain().getStep(),
            0
        );
    }
    return initializer;
}
```

**作用**：初始化数据库 Schema

#### 3.1.14 resolveInstanceId() 方法

```java
private String resolveInstanceId() {
    if (properties.getInstanceId() != null && !properties.getInstanceId().isBlank()) {
        return properties.getInstanceId();  // 使用配置的实例 ID
    }
    try {
        // IP:PID 格式
        return InetAddress.getLocalHost().getHostAddress() + ":" + ManagementFactory.getRuntimeMXBean().getName();
    } catch (UnknownHostException ignored) {
        return UUID.randomUUID().toString();  // 降级为 UUID
    }
}
```

---

## 四、生命周期管理

### 4.1 CosIdMachineIdLifecycle 类

**文件位置**：`config/CosIdMachineIdLifecycle.java`

#### 4.1.1 设计目的

管理 MachineId 的生命周期：
- **启动时**：分配 MachineId
- **关闭时**：回收 MachineId

#### 4.1.2 实现

```java
@Component
public class CosIdMachineIdLifecycle {
    
    private final MachineIdGuarder machineIdGuarder;
    private final JdbcMachineIdAllocator machineIdAllocator;
    
    @PreDestroy
    public void close() {
        // 1. 停止 Guarder
        machineIdGuarder.stop();
        
        // 2. 回收所有注册的实例
        for (Map.Entry<NamespacedInstanceId, GuardianState> entry : machineIdGuarder.getGuardianStates().entrySet()) {
            try {
                machineIdAllocator.revert(
                    entry.getKey().getNamespace(), 
                    entry.getKey().getInstanceId()
                );
            } catch (Throwable throwable) {
                // 忽略异常
            }
        }
    }
}
```

**关键点**：
- **@PreDestroy**：Spring 容器关闭时调用
- **先停止 Guarder**：防止心跳继续
- **再回收 MachineId**：释放资源

---

## 五、API 层详解

### 5.1 DTO 类

#### 5.1.1 GenerateIdReqDTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateIdReqDTO {
    @NotNull
    private IdGeneratorType type;
}
```

#### 5.1.2 BatchGenerateIdReqDTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchGenerateIdReqDTO {
    @NotNull
    private IdGeneratorType type;
    @Min(1)
    @Max(1000)
    private int size;
}
```

#### 5.1.3 BatchGenerateIdRspDTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchGenerateIdRspDTO {
    private IdGeneratorType type;
    private int size;
    private List<Long> ids;
}
```

#### 5.1.4 IdGeneratorType 枚举

```java
public enum IdGeneratorType {
    SNOWFLAKE,
    SEGMENT,
    SEGMENT_CHAIN
}
```

### 5.2 DistributedIdController 类

**文件位置**：`controller/DistributedIdController.java`

#### 5.2.1 nextId() 方法

```java
@PostMapping("/api/next-id")
public long nextId(@RequestBody GenerateIdReqDTO req) {
    return distributedIdGenerateService.nextId(req.getType());
}
```

**请求示例**：
```json
{
  "type": "SNOWFLAKE"
}
```

**响应示例**：
```json
1234567890123456789
```

#### 5.2.2 batchNextIds() 方法

```java
@PostMapping("/api/batch-next-ids")
public BatchGenerateIdRspDTO batchNextIds(@RequestBody BatchGenerateIdReqDTO req) {
    return distributedIdGenerateService.batchGenerateIds(req.getType(), req.getSize());
}
```

**请求示例**：
```json
{
  "type": "SEGMENT",
  "size": 10
}
```

**响应示例**：
```json
{
  "type": "SEGMENT",
  "size": 10,
  "ids": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
}
```

### 5.3 DistributedIdGenerateService 接口

```java
public interface DistributedIdGenerateService {
    
    long nextId(IdGeneratorType type);
    
    BatchGenerateIdRspDTO batchGenerateIds(IdGeneratorType type, int size);
}
```

### 5.4 DistributedIdGenerateServiceImpl 类

**文件位置**：`service/impl/DistributedIdGenerateServiceImpl.java`

#### 5.4.1 核心字段

```java
@Service
public class DistributedIdGenerateServiceImpl implements DistributedIdGenerateService {
    
    private static final int MAX_BATCH_SIZE = 1000;
    
    private final IdGenerator snowflakeIdGenerator;
    private final SegmentIdGenerator segmentIdGenerator;
    private final SegmentChainIdGenerator segmentChainIdGenerator;
}
```

#### 5.4.2 nextId() 方法

```java
@Override
public long nextId(IdGeneratorType type) {
    return generator(type).generate();
}
```

#### 5.4.3 batchGenerateIds() 方法

```java
@Override
public BatchGenerateIdRspDTO batchGenerateIds(IdGeneratorType type, int size) {
    // 1. 参数校验
    if (size <= 0 || size > MAX_BATCH_SIZE) {
        throw new IllegalArgumentException("batch size must be between 1 and " + MAX_BATCH_SIZE + ".");
    }
    
    // 2. 获取生成器
    IdGenerator generator = generator(type);
    
    // 3. 批量生成
    List<Long> ids = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
        ids.add(generator.generate());
    }
    
    // 4. 返回结果
    return BatchGenerateIdRspDTO.builder()
            .type(type)
            .size(size)
            .ids(ids)
            .build();
}
```

#### 5.4.4 generator() 方法

```java
private IdGenerator generator(IdGeneratorType type) {
    if (type == null) {
        throw new IllegalArgumentException("type can not be null.");
    }
    
    IdGenerator generator = switch (type) {
        case SNOWFLAKE -> snowflakeIdGenerator;
        case SEGMENT -> segmentIdGenerator;
        case SEGMENT_CHAIN -> segmentChainIdGenerator;
    };
    
    if (generator == null) {
        throw new GeneratorUnavailableException("Id generator " + type + " is unavailable.");
    }
    
    return generator;
}
```

### 5.5 GlobalExceptionHandler 类

**文件位置**：`exception/GlobalExceptionHandler.java`

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(Throwable.class)
    public Map<String, Object> handle(Throwable ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ts", Instant.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        body.put("path", Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .map(ra -> ((ServletRequestAttributes) ra).getRequest().getRequestURI())
                .orElse("unknown"));
        return body;
    }
}
```

---

## 六、Schema 初始化

### 6.1 JdbcIdGeneratorSchemaInitializer 类

**文件位置**：`jdbc/JdbcIdGeneratorSchemaInitializer.java`

#### 6.1.1 initialize() 方法

```java
public void initialize(String namespace, String segmentName, long segmentStep, String segmentChainName, long segmentChainStep, long defaultMaxId) {
    try (Connection connection = dataSource.getConnection()) {
        // 1. 创建 cosid_machine 表
        createCosIdMachineTable(connection);
        
        // 2. 创建 cosid 表
        createCosIdTable(connection);
        
        // 3. 初始化号段数据
        initSegment(connection, namespace, segmentName, segmentStep, defaultMaxId);
        initSegment(connection, namespace, segmentChainName, segmentChainStep, defaultMaxId);
    } catch (SQLException e) {
        throw new IdGeneratorException("Failed to initialize schema", e);
    }
}
```

#### 6.1.2 createCosIdMachineTable() 方法

```java
private void createCosIdMachineTable(Connection connection) throws SQLException {
    String sql = "CREATE TABLE IF NOT EXISTS cosid_machine (" +
        "name VARCHAR(128) NOT NULL, " +
        "namespace VARCHAR(128) NOT NULL, " +
        "machine_id INT NOT NULL, " +
        "instance_id VARCHAR(128) NOT NULL, " +
        "last_timestamp BIGINT NOT NULL, " +
        "distribute_time BIGINT NOT NULL, " +
        "revert_time BIGINT NOT NULL, " +
        "PRIMARY KEY (namespace, machine_id))";
    
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.executeUpdate();
    }
}
```

#### 6.1.3 createCosIdTable() 方法

```java
private void createCosIdTable(Connection connection) throws SQLException {
    String sql = "CREATE TABLE IF NOT EXISTS cosid (" +
        "name VARCHAR(128) NOT NULL, " +
        "namespace VARCHAR(128) NOT NULL, " +
        "last_max_id BIGINT NOT NULL, " +
        "last_fetch_time BIGINT NOT NULL, " +
        "PRIMARY KEY (namespace, name))";
    
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.executeUpdate();
    }
}
```

#### 6.1.4 initSegment() 方法

```java
private void initSegment(Connection connection, String namespace, String name, long step, long defaultMaxId) throws SQLException {
    String checkSql = "SELECT COUNT(*) FROM cosid WHERE namespace = ? AND name = ?";
    try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
        checkStmt.setString(1, namespace);
        checkStmt.setString(2, name);
        ResultSet rs = checkStmt.executeQuery();
        if (rs.next() && rs.getInt(1) == 0) {
            String insertSql = "INSERT INTO cosid (namespace, name, last_max_id, last_fetch_time) VALUES (?, ?, ?, ?)";
            try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                insertStmt.setString(1, namespace);
                insertStmt.setString(2, name);
                insertStmt.setLong(3, defaultMaxId);
                insertStmt.setLong(4, System.currentTimeMillis());
                insertStmt.executeUpdate();
            }
        }
    }
}
```

---

## 七、配置示例

### 7.1 application.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/distributed_id?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

distributed-id:
  namespace: xiaohashu
  stable-instance: true
  
  jdbc:
    initialize-schema: true
  
  snowflake:
    enabled: true
    epoch: "2021-01-01T00:00:00Z"
    timestamp-bit: 41
    machine-bit: 10
    sequence-bit: 12
    sequence-reset-threshold: 2047
    clock-sync: true
    clock-spin-threshold: 1
    clock-broken-threshold: 500
    safe-guard-duration: 5m
    guarder-initial-delay: 1m
    guarder-delay: 1m
  
  segment:
    enabled: true
    name: "segment"
    step: 10000
    ttl-seconds: 9223372036854775807
  
  segment-chain:
    enabled: true
    name: "segment-chain"
    step: 10000
    ttl-seconds: 9223372036854775807
    safe-distance: 2
    prefetch-period: 1s
    worker-core-pool-size: 8
```

### 7.2 使用示例

#### 7.2.1 单个 ID 生成

```bash
curl -X POST http://localhost:8080/api/next-id \
  -H "Content-Type: application/json" \
  -d '{"type": "SNOWFLAKE"}'
```

#### 7.2.2 批量 ID 生成

```bash
curl -X POST http://localhost:8080/api/batch-next-ids \
  -H "Content-Type: application/json" \
  -d '{"type": "SEGMENT", "size": 100}'
```

---

## 八、启动流程

### 8.1 完整启动流程

```
Spring Boot 启动
    │
    ├── 1. 加载配置
    │   └── DistributedIdProperties
    │
    ├── 2. 校验配置
    │   └── @PostConstruct validate()
    │
    ├── 3. 初始化 Schema
    │   └── JdbcIdGeneratorSchemaInitializer.initialize()
    │       ├── CREATE TABLE cosid_machine
    │       ├── CREATE TABLE cosid
    │       └── INSERT INTO cosid (初始号段)
    │
    ├── 4. 创建 Bean
    │   ├── DefaultClockBackwardsSynchronizer
    │   ├── JdbcMachineIdAllocator
    │   ├── MachineIdGuarder
    │   ├── GuardDistribute
    │   ├── CosIdMachineIdLifecycle
    │   ├── segmentJdbcAllocator
    │   ├── segmentChainJdbcAllocator
    │   ├── PrefetchWorkerExecutorService
    │   ├── snowflakeIdGenerator
    │   │   ├── resolveInstanceId()
    │   │   ├── guardDistribute.distribute()
    │   │   └── new SnowflakeIdGenerator()
    │   ├── segmentIdGenerator
    │   └── segmentChainIdGenerator
    │
    ├── 5. 启动 Guarder
    │   └── machineIdGuarder.start()
    │
    └── 6. 应用就绪
        └── 接受请求
```

### 8.2 SnowflakeIdGenerator 创建流程

```
snowflakeIdGenerator Bean
    │
    ├── 1. resolveInstanceId()
    │   └── IP:PID 或 UUID
    │
    ├── 2. guardDistribute.distribute()
    │   ├── machineIdAllocator.distribute()
    │   │   ├── 检查本地存储
    │   │   ├── distributeBySelf
    │   │   ├── distributeByRevert
    │   │   └── distributeMachine
    │   └── machineIdGuarder.register()
    │
    ├── 3. new SnowflakeIdGenerator()
    │   └── 使用分配的 machineId
    │
    └── 4. 包装成 ClockSyncSnowflakeIdGenerator (可选)
```

---

## 九、关闭流程

### 9.1 完整关闭流程

```
Spring Boot 关闭
    │
    ├── 1. @PreDestroy CosIdMachineIdLifecycle.close()
    │   ├── machineIdGuarder.stop()
    │   └── machineIdAllocator.revert()
    │
    ├── 2. PrefetchWorkerExecutorService.shutdown()
    │   └── 停止预取线程池
    │
    └── 3. 应用关闭
```

---

## 十、与 CosId 的对比

### 10.1 配置属性

**完全一致**。所有配置参数与 CosId 相同。

### 10.2 Bean 配置

**完全一致**。所有 Bean 定义与 CosId 相同。

### 10.3 生命周期管理

**完全一致**。都是 @PreDestroy 时停止 Guarder 并回收 MachineId。

### 10.4 Schema 初始化

**完全一致**。都是创建相同的表结构。

---

## 十一、总结

Spring Boot 集成的核心要点：

1. **自动配置**：通过 @Configuration 和 @Bean 自动创建所有组件
2. **配置灵活**：通过 @ConfigurationProperties 支持多种配置方式
3. **生命周期管理**：通过 @PreDestroy 自动管理 MachineId 的分配和回收
4. **Schema 初始化**：自动创建数据库表结构
5. **API 友好**：提供简洁的 REST API

通过这些机制，实现了开箱即用的分布式 ID 生成器。
