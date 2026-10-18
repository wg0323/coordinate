# 共享单车实时位置监管平台（面试全覆盖版）

> 以一个可本地运行、可演示、可讲解的完整系统，覆盖 `docs/prompt.md` 全部 11 道坐标/围栏面试题 + 4 条知识点。设备模拟器生产 GPS → Kafka 削峰 → Flink 清洗/围栏/热力聚合 → REST/SSE → Leaflet 地图，全链路可跑通、可压测、可对照面试题演示。

## 📋 目录

- [项目简介](#项目简介)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [演示对照（11 道面试题）](#演示对照11-道面试题)
- [部署指南](#部署指南)
- [API 文档](#api-文档)
- [常见问题](#常见问题)

## 项目简介

项目为面试准备而建，目标是"可运行、可演示、可讲解"：每个面试题背后都有对应的真实代码、可执行的演示操作和实测数字（如 10 万围栏 STRtree 加速 46 倍、链路稳态处理 ~8k 条/s）。

核心文档：

- 学习手册（主线）：`docs/handbook.md`，从零讲原理 + 踩坑实录 + 面试逐题速答
- 设计方案：`docs/design.md`（含收益/成本裁决与演进预案）
- 实施计划：`docs/plans/2026-10-14-coordinates-system.md`
- 运维手册：`docs/ops.md`
- 面试题原文：`docs/prompt.md`

### 核心功能

- [x] 设备模拟器：北京 12 区县 1:10 建模（8.48 万辆），5s/50m 双触发上报 + 漂移注入 + 伴随车辆场景
- [x] GPS 清洗：速度极限过滤 → 卡尔曼滤波 → 5 点中值滤波（GpsCleanJob）
- [x] 电子围栏：广播围栏 + STRtree 粗筛 + JTS covers 精筛 + 事件时间定时器防抖（FenceJob）
- [x] 热力聚合：H3 res8 + 1min 滚动窗口 distinct 车辆数（H3AggJob）
- [x] 后端：REST 9 组接口 + SSE 实时告警推送 + 告警 MongoDB 归档
- [x] 前端监控大屏：中心辐射型深色大屏（顶栏/左右面板/中央地图/底部数据带），车辆状态着色（绿=空闲/灰=骑行中）+ zoom 联动车辆热力云 + 围栏装载状态着色 + ECharts 图表 + SSE 实时告警
- [x] 真实围栏数据：2017 年监管平台 b_fence 数据 9093 条经清洗后入库 6917 条（GCJ-02 → WGS84）
- [x] 轨迹存储冷热分离：Redis GEO（热）+ TDengine（冷，KEEP 90d）+ MongoDB（属性/围栏/告警）

## 系统架构

```text
[device-simulator 设备模拟器]  车队台账存 MongoDB(bikes, 8.48万辆, 首次启动自动seed) 12 区县建模 5s/50m 双触发上报 + 漂移注入 + 伴随车辆场景
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
[frontend Vue3+TS+Vite]   监控大屏：深色天地图 + 车辆状态散点(Canvas) + 围栏状态着色 + 车辆热力云 + ECharts 面板 + SSE 告警
```

## 技术栈

| 分类 | 技术 | 版本 |
| ---- | ---- | ---- |
| 语言 | Java | 1.8 |
| 流计算 | Apache Flink | 1.17.2 |
| 后端框架 | Spring Boot | 2.7.18 |
| 消息队列 | Kafka（wurstmeister 镜像） | 2.13-2.8.1 |
| 缓存/Geo | Redis（Jedis 客户端 4.4.6） | 6.2.6 |
| 时序库 | TDengine（taos-jdbcdriver 3.2.7） | 3.3.3.0 |
| 文档库 | MongoDB | 6.2.6 |
| 注册协调 | Zookeeper（bitnami 镜像，Kafka 依赖） | 3.5.9 |
| 地理计算 | JTS | 1.19.0 |
| 地理索引 | H3 | 4.1.1 |
| 前端框架 | Vue 3 + TypeScript | 3.4.x / 5.4.x |
| 构建工具 | Vite | 5.2.x |
| 地图 | Leaflet + leaflet-draw + leaflet.heat | 1.9.4 |
| 底图 | 天地图（vec_w + cva_w，CGCS2000 当 WGS84 用） | - |
| 接口文档 | Knife4j（OpenAPI3 starter） | 4.5.0 |

## 项目结构

```text
coordinates/
├── coordinate-common/   # 公共库：GeoHash 九宫格、坐标三系转换、射线法判定、消息模型
├── device-simulator/    # 设备模拟器：车辆建模、双触发上报、漂移/伴随车辆场景注入
├── flink-jobs/          # 三个 Flink 作业：GpsCleanJob / FenceJob / H3AggJob
├── api-server/          # Spring Boot 后端：REST 9 组接口 + SSE + 告警归档
├── frontend/            # Vue3 监控大屏：深色地图、状态散点、围栏着色、ECharts 面板、SSE 告警
├── scripts/             # deploy-helper.sh（VM 一键启停/作业部署）、docker-compose.yml、围栏导入/体检工具
├── docs/                # handbook / design / ops / prompt / b_fence.sql / plans/
└── pom.xml              # Maven 多模块父 POM（groupId com.bike.monitor）
```

## 环境要求

环境分工：**中间件与 Flink 全部运行在虚拟机 192.168.1.25（CentOS 7，8G 内存）**；开发机跑模拟器、api-server、前端，通过 IP 访问虚拟机。

- 虚拟机：Docker + docker compose（compose 文件即 `scripts/docker-compose.yml`）
- 开发机：JDK 8 + Maven 3.9.8、Node ≥ 18
- 内存预算（8G，宿主 + Docker 留 ~2G）：Redis 256m｜MongoDB 512m｜TDengine 768m｜ZK 512m｜Kafka 768m｜Flink JM 1400m + TM 1700m，slot 9 = 三作业并行度之和（4+3+2）

## 快速开始

### 虚拟机侧（/opt 目录）

文件约定：`docker-compose.yml`（即 `scripts/docker-compose.yml`）、`deploy-helper.sh`、`.env`（内容 `KAFKA_HOST_IP=192.168.1.25`）、`data/`（容器数据 + Flink checkpoint）。`deploy-helper.sh` 从 Windows 传入后先执行 `sed -i 's/\r$//' deploy-helper.sh` 去换行符。

```bash
# 启动：拉起全部容器 + 自动提交缺失的 Flink 作业
#（Kafka 因 ZK 残留节点起不来时会自动清理 /brokers/ids/1 并重试）
./deploy-helper.sh

# 停止：Kafka 优雅关闭（注销 ZK 注册）后容器停止，数据在 ./data/ 不丢失
# 禁止用 docker compose down：会重建容器，TDengine 按 FQDN 落盘的元数据将读不出
./deploy-helper.sh stop

# 创建 topic（必须预建：Flink KafkaSource 禁用了自动创建，缺 topic 作业会陷入重启循环）
# retention.ms=2h + segment.ms=10min：磁盘自清理，稳态上限约 10GB
for t in "bike-gps-raw 4" "bike-gps-clean 4" "bike-fence-alert 2"; do
  set -- $t
  docker exec kafka kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists \
    --topic $1 --partitions $2 --replication-factor 1 \
    --config retention.ms=7200000 --config segment.ms=600000
done
```

注意：`fence-config` topic 由 api-server/FenceJob 交互产生，清 topic 时必须保留——FenceJob 无状态恢复，靠重放 `fence-config` 拿围栏规则。

### 围栏数据导入（可选）

`docs/b_fence.sql` 是 2017 年监管平台的真实围栏数据（9093 条，GCJ-02 坐标系）。在开发机执行导入脚本，自动完成清洗（status=-1 剔除、面积 <0.5m² 剔除、相邻重复点、自相交）与坐标转换，最终入库 6917 条：

```bash
node scripts/import-fences.mjs
```

`scripts/check-invalid.mjs` 可对几何做体检。不导入也可用，前端画围栏 → `POST /api/fence` 同样落库并广播给 FenceJob。

### 开发机侧

```bash
# 1. 更新 Flink 程序：打包 → 传虚拟机 → 部署（见「部署指南」）

# 2. 启动模拟器（--kafka 指向虚拟机；speed 20 / ratio 0.1 实测稳态 ~2.5k 条/s，链路能力 ~8k/s。
#    ratio 0.2 生产爬到 ~9k/s 贴能力线；speed 60 冲 ~28k/s 必积压，仅短时压测。
#    车队台账存 MongoDB bikes 集合：首次启动自动 seed 全量 8.48 万辆，此后从库加载；
#    vehicle-ratio 为活跃车辆抽样比例。--mongo 默认 mongodb://192.168.1.25:27017/bike_monitor）
java -jar device-simulator\target\device-simulator-1.0.0-SNAPSHOT.jar --kafka=192.168.1.25:9092 --speed-factor=20 --vehicle-ratio=0.1

# 3. 启动后端（application.yml 的 Redis/Mongo/Kafka/TDengine 已指向 192.168.1.25）
java -jar api-server\target\api-server-1.0.0-SNAPSHOT.jar

# 4. 启动前端（开发期 Vite proxy /api → 8080）
cd frontend && npm install && npm run dev     # → http://localhost:5173
```

## 配置说明

| 配置项 | 说明 | 默认值 |
| ---- | ---- | ---- |
| `api-server/src/main/resources/application.yml` 的 `spring.redis.host` | Redis 地址 | 192.168.1.25 |
| 同文件 `spring.data.mongodb.uri` | MongoDB 连接（库 bike_monitor） | mongodb://192.168.1.25:27017/bike_monitor |
| 同文件 `spring.kafka.bootstrap-servers` | Kafka 地址 | 192.168.1.25:9092 |
| 同文件 `tdengine.url` | TDengine REST 连接（root/taosdata） | jdbc:TAOS-RS://192.168.1.25:6041 |
| `/opt/.env` 的 `KAFKA_HOST_IP` | Kafka 对宿主机广播地址，需与 VM IP 一致 | 192.168.1.25 |
| `frontend/src/components/MapView.vue` 的 `tk` | 天地图 key（已申请，demo 用途硬编码；生产应绑域名白名单） | 见文件 |
| `knife4j.production=true` | 生产环境关闭在线文档 | 未开启 |

## 演示对照（11 道面试题）

| 演示 | 操作 |
| --- | --- |
| 题 1 清洗 | 对比 `kafka-console-consumer` 读 raw 与 clean 两个 topic：注入的瞬移/横跳点被剔除 |
| 题 2 抽稀 | 页面「轨迹回放」输入 bikeId，拖动阈值滑杆，观察点数 13→4 |
| 题 3 相似度 | `GET /api/similarity?bikeA=..&bikeB=..`（配合模拟器伴随车辆场景=套牌车） |
| 题 4 坐标系 | `GET /api/coord/convert?lng=..&lat=..` 同点三系对比 |
| 题 5 存储 | Redis `bike:latest`（热）/ TDengine `gps_trace`（冷, KEEP 90d）/ MongoDB（属性） |
| 题 6 GeoHash | `coordinate-common/GeoHash.java` 九宫格实现；页面热力云（leaflet.heat 核密度，默认关闭、勾选开启，zoom<11 自动显示） |
| 题 7 选型 | 四种存储各司其职，「系统架构」图即答案 |
| 题 8 射线法 | `RayCastingTest`：凹多边形/边界/顶点 20 用例，与 JTS covers 对照 |
| 题 9 10 万围栏 | `FenceIndexBenchmark`：STRtree 5.5µs/点 vs 全量 254µs，加速 46 倍 |
| 题 10 Flink 报警 | 页面画围栏 → SSE 实时弹 ENTER/EXIT → Mongo alerts 归档 |
| 题 11 系统设计 | 全链路：双触发上报→削峰→H3 聚合/实时点位→热力云→bbox 点位接口 |

## 部署指南

更新 Flink 程序（开发机打包 → 传虚拟机 → 一键重部署）：

```bash
# 开发机：打包（必须带 clean，见常见问题）→ 传 jar
mvn clean package -pl flink-jobs -am
scp flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar root@192.168.1.25:/opt/
```

```bash
# 虚拟机：--fresh = 取消在跑作业、覆盖 jar、重新提交；日常启动不带 jar 不带 --fresh
./deploy-helper.sh ./flink-jobs-1.0.0-SNAPSHOT.jar --fresh
```

根 POM 已设 `maven.test.skip=true`，跑测试用 `mvn test -Dmaven.test.skip=false`。

## API 文档

启动后端后访问：`http://localhost:8080/doc.html`（Knife4j）。生产环境用 `knife4j.production=true` 关闭。

## 常见问题

**Q: Flink 作业 RUNNING 但不消费？**
A: 三作业共存要求 TM slot ≥ 各作业顶点并行度之和（4+3+2=9），先查 `NoResourceAvailableException`；再查 topic 是否存在。判断消费健康只看下游 topic 的 log-end 增速，消费组 lag/offset 因 checkpoint 批量提交不可信。详见 `docs/ops.md`。

**Q: KafkaSource 报 `UnknownTopicOrPartitionException` 陷入 RESTARTING 循环？**
A: topic 必须预建（Flink KafkaSource 显式禁用了自动创建），按上面命令创建对应分区数。

**Q: 容器起不来或写不了数据（首次搭建/迁移）？**
A: bind mount 权限坑：bitnami 容器（mongodb/zookeeper）数据目录 `chown -R 1001:1001`；Flink checkpoint 目录 `chown -R 9999:9999`（容器内 flink 用户 uid=9999）。

**Q: 运行时报 `ClassFormatError: Invalid method Code length 0`？**
A: `target/classes` 曾被 IDE 编译产物污染，打包必须带 `clean`。

**Q: Kafka 非正常关闭后起不来？**
A: ZK 残留 `/brokers/ids/1` 导致，`deploy-helper.sh` 已内置自动清理重试。

**Q: 模拟器的 ts 比当前时间快？**
A: 设计行为：`simMs = realMs × speedFactor`（speed-factor=20 即 1 真实秒 ≈ 20 模拟秒），不是时钟漂移。
