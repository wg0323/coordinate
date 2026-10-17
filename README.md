# 共享单车实时位置监管平台（面试全覆盖版）

以一个可本地运行、可演示、可讲解的完整系统，覆盖 `docs/prompt.md` 全部 **11 道面试题 + 4 条知识点**。

设计方案：`docs/design.md`（含收益/成本裁决与演进预案）｜实施计划：`docs/plans/2026-10-14-coordinates-system.md`
运维手册：`docs/ops.md`
**新手入口：`docs/handbook.md` 学习手册**（从零讲原理 + 12 个踩坑实录 + 面试逐题速答，主线文档）

## 架构

```
[device-simulator 设备模拟器]  12 区县建模(1:10, 8.48 万辆) 5s/50m 双触发上报 + 漂移注入 + 伴随车辆场景
        │ Kafka bike-gps-raw（削峰）
        ▼
[flink-jobs]  ①GpsCleanJob  速度极限过滤→卡尔曼滤波→5点中值滤波
              │               ├─ bike-gps-clean  ├─ Redis GEO bike:latest  └─ TDengine gps_trace(KEEP 90d)
              ②FenceJob       广播围栏 + STRtree 粗筛 + JTS covers 精筛 + 事件时间定时器防抖
              │               └─ bike-fence-alert
              ③H3AggJob       H3 res8 + 1min 滚动窗口 distinct 车辆数 → Redis heat:*
        ▼
[api-server Spring Boot]  REST 9 组接口 + 告警归档(MongoDB) + SSE 实时推送
        ▼
[frontend Vue3+TS+Vite]   Leaflet 地图 + 热力图(leaflet.heat) + 轨迹回放 + 围栏绘制 + SSE 告警
```

存储：Redis GEO（热）+ TDengine（冷轨迹）+ MongoDB（属性/围栏/告警），冷热分离（题 5）。

## 快速开始

环境分工：**中间件与 Flink 全部运行在虚拟机 192.168.1.25（CentOS7，8G 内存）**；开发机跑模拟器、api-server、前端，通过 IP 访问虚拟机。

前置：虚拟机已装 Docker；开发机 JDK 8 + Maven 3.9.8、Node ≥18。

### 虚拟机侧（/opt 目录）

文件约定：`docker-compose.yml`（即 `scripts/docker-compose.yml`）、`vm-start.sh`、`.env`（内容 `KAFKA_HOST_IP=192.168.1.25`）、`data/`（容器数据 + Flink checkpoint）。`vm-start.sh` 从 Windows 传入后先执行 `sed -i 's/\r$//' vm-start.sh` 去换行符。

```bash
# 启动：拉起全部容器 + 自动提交缺失的 Flink 作业
#（Kafka 因 ZK 残留节点起不来时会自动清理 /brokers/ids/1 并重试）
./vm-start.sh

# 停止：容器停止但保留，数据在 ./data/ 不丢失
docker compose -f docker-compose.yml stop

# 创建 topic（必须预建：Flink KafkaSource 禁用了自动创建，缺 topic 作业会陷入重启循环）
for t in "bike-gps-raw 4" "bike-gps-clean 4" "bike-fence-alert 2"; do
  set -- $t
  docker exec kafka kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic $1 --partitions $2 --replication-factor 1
done
```

内存分配（8G 预算，宿主+Docker 留 ~2G）：Redis 256m｜MongoDB 512m｜TDengine 768m｜ZK 512m｜Kafka 768m｜Flink JM 1400m + TM 1700m，slot 9 = 三作业并行度之和（4+3+2）。

### 开发机侧

```bash
# 1. 更新 Flink 程序：打包 → 传虚拟机 → 部署
#    （--fresh = 取消在跑作业、覆盖 jar、重新提交；日常启动不带 jar 不带 --fresh）
mvn clean package -pl flink-jobs -am
scp flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar root@192.168.1.25:/opt/
# 虚拟机上执行：
./vm-start.sh ./flink-jobs-1.0.0-SNAPSHOT.jar --fresh

# 2. 启动模拟器（--kafka 指向虚拟机；speed 20 / ratio 0.1 实测稳态 ~2.5k 条/s，链路能力 ~8k/s。
#    ratio 0.2 生产爬到 ~9k/s 贴能力线；speed 60 冲 ~28k/s 必积压，仅短时压测）
java -jar device-simulator\target\device-simulator-1.0.0-SNAPSHOT.jar --kafka=192.168.1.25:9092 --speed-factor=20 --vehicle-ratio=0.1

# 3. 启动后端（application.yml 的 Redis/Mongo/Kafka/TDengine 已指向 192.168.1.25）
java -jar api-server\target\api-server-1.0.0-SNAPSHOT.jar

# 4. 启动前端（开发期 Vite proxy /api → 8080）
cd frontend && npm install && npm run dev     → http://localhost:5173
```

Flink UI：http://192.168.1.25:8081。API 在线文档（Knife4j）：http://localhost:8080/doc.html（生产环境用 `knife4j.production=true` 关闭）。

## 演示脚本（对照 11 题）

| 演示 | 操作 |
|---|---|
| 题 1 清洗 | 对比 `kafka-console-consumer` 读 raw 与 clean 两个 topic：注入的瞬移/横跳点被剔除 |
| 题 2 抽稀 | 页面「轨迹回放」输入 bikeId，拖动阈值滑杆，观察点数 13→4 |
| 题 3 相似度 | `GET /api/similarity?bikeA=..&bikeB=..`（配合模拟器伴随车辆场景=套牌车） |
| 题 4 坐标系 | `GET /api/coord/convert?lng=..&lat=..` 同点三系对比 |
| 题 5 存储 | Redis `bike:latest`（热）/ TDengine `gps_trace`（冷, KEEP 90d）/ MongoDB（属性） |
| 题 6 GeoHash | `coordinate-common/GeoHash.java` 九宫格实现；页面热力图（H3 进阶方案） |
| 题 7 选型 | 四种存储各司其职，README 架构图即答案 |
| 题 8 射线法 | `RayCastingTest`：凹多边形/边界/顶点 20 用例，与 JTS covers 对照 |
| 题 9 10 万围栏 | `FenceIndexBenchmark`：STRtree 5.5µs/点 vs 全量 254µs，加速 46 倍 |
| 题 10 Flink 报警 | 页面画围栏 → SSE 实时弹 ENTER/EXIT → Mongo alerts 归档 |
| 题 11 系统设计 | 全链路：双触发上报→削峰→H3 聚合→热力图→bbox 点位接口 |

## 已知问题与排查

- 三作业共存要求 TM slot ≥ 各作业顶点并行度之和（4+3+2=9，虚拟机版按 8G 内存预算恰好配 9）。作业「RUNNING 但不消费」
  优先查 `NoResourceAvailableException` 与 topic 是否存在，详见 `docs/ops.md`。
- topic 必须预建：Flink KafkaSource 禁用了自动创建，消费不存在的 topic 会陷入 RESTARTING 重启循环（报
  `UnknownTopicOrPartitionException`）。
- bind mount 权限坑（首次搭建/迁移）：bitnami 容器（mongodb/zookeeper）数据目录 `chown -R 1001:1001`；
  Flink checkpoint 目录 `chown -R 9999:9999`（容器内 flink 用户 uid=9999）。
- 打包必须带 `clean`：`target/classes` 曾被 IDE 编译污染，产物运行时报 `ClassFormatError: Invalid method Code length 0`。
- Kafka 非正常关闭后 ZK 残留 `/brokers/ids/1` 会导致起不来，`vm-start.sh` 已内置自动清理重试。
