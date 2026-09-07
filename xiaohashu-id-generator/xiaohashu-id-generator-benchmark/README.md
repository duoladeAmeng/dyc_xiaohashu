# xiaohashu-id-generator-benchmark

本模块提供 JMH benchmark 工程，只用于生成真实压测数据；仓库内不提交、不编造 benchmark 结果。

## Benchmark Scope

当前覆盖三类 core 生成器：

- `snowflake`
- `segmentId`
- `segmentChainId`

Benchmark 使用内存版 `SegmentAllocator`，用于隔离核心生成算法、CAS、锁竞争和 SegmentChain 预取逻辑的开销。JDBC/MySQL 性能应单独用数据库集成压测评估，因为结果会强依赖 MySQL、连接池、磁盘和网络环境。

## Build

```powershell
mvn -pl xiaohashu-id-generator-benchmark -am -DskipTests package
```

构建完成后会生成：

```text
xiaohashu-id-generator-benchmark/target/benchmarks.jar
```

## List Benchmarks

```powershell
java -jar xiaohashu-id-generator-benchmark/target/benchmarks.jar -l
```

## Throughput

单线程：

```powershell
java -jar xiaohashu-id-generator-benchmark/target/benchmarks.jar ".*IdGeneratorBenchmark.*" -bm thrpt -tu ns -wi 5 -i 5 -f 2 -t 1
```

多线程，线程数等于可用 CPU：

```powershell
java -jar xiaohashu-id-generator-benchmark/target/benchmarks.jar ".*IdGeneratorBenchmark.*" -bm thrpt -tu ns -wi 5 -i 5 -f 2 -t $env:NUMBER_OF_PROCESSORS
```

## p50 / p99 Latency

JMH 的 `SampleTime` 模式会输出采样分布和百分位。单线程：

```powershell
java -jar xiaohashu-id-generator-benchmark/target/benchmarks.jar ".*IdGeneratorBenchmark.*" -bm sample -tu ns -wi 5 -i 5 -f 2 -t 1
```

多线程，线程数等于可用 CPU：

```powershell
java -jar xiaohashu-id-generator-benchmark/target/benchmarks.jar ".*IdGeneratorBenchmark.*" -bm sample -tu ns -wi 5 -i 5 -f 2 -t $env:NUMBER_OF_PROCESSORS
```

输出中重点查看：

- `Score`：采样平均耗时。
- `p0.50`：p50。
- `p0.99`：p99。

## CSV Output

```powershell
java -jar xiaohashu-id-generator-benchmark/target/benchmarks.jar ".*IdGeneratorBenchmark.*" -tu ns -wi 5 -i 5 -f 2 -t $env:NUMBER_OF_PROCESSORS -rf csv -rff target/id-generator-jmh.csv
```
