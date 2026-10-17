#!/bin/bash
# 虚拟机一键脚本：启动 / 优雅关闭 / Flink 作业部署（配合同目录 docker-compose.yml，与 .env 一起使用）
#
# 日常启动（不重新部署作业）:
#   ./deploy-helper.sh
#
# 优雅关闭（先让 Kafka 正常注销 ZK，再停全部容器）:
#   ./deploy-helper.sh stop
#
# 重新打包后部署新作业 jar（在 Windows 上 mvn package 后 scp 到虚拟机）:
#   ./deploy-helper.sh /opt/flink-jobs/flink-jobs-1.0.0-SNAPSHOT.jar --fresh
#
# 说明:
#   --fresh    先取消所有在跑作业，再部署并提交新 jar（jar 更新后必须用这个）
#   不带 --fresh 时，仅拷贝 jar 并提交"缺失"的作业，不影响在跑作业

set -u
cd "$(dirname "$0")"

# ---------- stop 子命令：优雅关闭 ----------
if [ "${1:-}" = "stop" ]; then
    echo "[..] Kafka 优雅关闭（等 broker 注销 ZK 注册）..."
    docker exec kafka kafka-server-stop.sh >/dev/null 2>&1 || echo "[WARN] kafka-server-stop.sh 执行失败（容器未在跑？）"
    for i in $(seq 1 10); do
        docker exec kafka bash -c 'echo > /dev/tcp/127.0.0.1/9092' >/dev/null 2>&1 || break
        sleep 2
    done
    docker compose -f docker-compose.yml stop
    echo "[OK] 已全部停止（数据保留在 ./data/，下次 ./deploy-helper.sh 恢复）"
    exit 0
fi

JAR=""
FRESH=false
for a in "$@"; do
    case "$a" in
        --fresh) FRESH=true ;;
        -*) echo "未知参数: $a（支持 --fresh）"; echo "用法: ./deploy-helper.sh [jar路径] [--fresh] 或 ./deploy-helper.sh stop"; exit 1 ;;
        *) JAR="$a" ;;
    esac
done

ok()   { echo "[OK] $1"; }
fail() { echo "[FAIL] $1"; exit 1; }

# ---------- 1. 启动容器 ----------
docker compose -f docker-compose.yml up -d || fail "docker compose 启动失败"

# ---------- 2. Kafka 就绪自愈（先判断 ZK 残留是否真实存在，避免假阳性清理） ----------
kafka_ready() { docker exec kafka kafka-broker-api-versions.sh --bootstrap-server kafka:9092 >/dev/null 2>&1; }
# zkCli 的结果行以 [ 开头（如 "[1]" 或 "[]"），合并 stderr 防输出丢失；取最后一个 [ 行判断
zk_broker_ids() { docker exec zookeeper zkCli.sh ls /brokers/ids 2>&1 | grep -E '^\[' | tail -1; }

echo "[..] 等待 Kafka 就绪..."
for i in $(seq 1 60); do kafka_ready && break; sleep 2; done
if ! kafka_ready; then
    ids=$(zk_broker_ids)
    if echo "$ids" | grep -q '\[1\]'; then
        echo "[WARN] ZK 存在残留 /brokers/ids/1（broker 无法注册），清理后重启 Kafka"
        docker exec zookeeper zkCli.sh rmr /brokers/ids/1 >/dev/null 2>&1
        docker restart kafka >/dev/null
        for i in $(seq 1 60); do kafka_ready && break; sleep 2; done
        kafka_ready || { docker logs kafka --tail 30; fail "Kafka 清理残留后仍未就绪"; }
    else
        echo "[WARN] Kafka 迟迟未就绪，ls /brokers/ids = ${ids:-<zkCli 无输出，检查 zookeeper 容器>}（无残留则多半在恢复日志）"
        docker logs kafka --tail 5 2>&1
        for i in $(seq 1 60); do kafka_ready && break; sleep 2; done
        kafka_ready || { docker logs kafka --tail 30; fail "Kafka 仍未就绪，请按上方日志排查"; }
    fi
fi
ok "中间件已启动 (Redis/MongoDB/TDengine/ZK/Kafka)"

# ---------- 3. Flink REST 就绪 ----------
for i in $(seq 1 30); do curl -sf http://localhost:8081/overview >/dev/null && break; sleep 2; done
curl -sf http://localhost:8081/overview >/dev/null || { docker logs flink-jobmanager --tail 30; fail "Flink REST 未就绪"; }
ok "Flink 集群已启动 (UI: http://localhost:8081)"

# ---------- 4. 作业部署 ----------
if [ -z "$JAR" ]; then
    echo "[SKIP] 未指定 jar，跳过作业部署。重新打包后: ./deploy-helper.sh <jar路径> --fresh"
else
    [ -f "$JAR" ] || fail "jar 不存在: $JAR"
    NAME=$(basename "$JAR")

    if $FRESH; then
        # 取消所有在跑作业
        ids=$(docker exec flink-jobmanager flink list 2>/dev/null | grep -oE '[0-9a-f]{32}') || true
        for id in $ids; do
            echo "[..] 取消作业 $id"
            docker exec flink-jobmanager flink cancel "$id" >/dev/null 2>&1
        done
        [ -n "$ids" ] && sleep 8
    fi

    # 覆盖拷贝 jar 进 jobmanager（usrlib 在容器重建后会清空，每次都拷）
    docker exec flink-jobmanager mkdir -p /opt/flink/usrlib
    docker cp "$JAR" "flink-jobmanager:/opt/flink/usrlib/$NAME" || fail "jar 拷贝失败"

    # 提交缺失的作业（flink list 输出形如: 17.10.2026 07:23:21 : GpsCleanJob (<32位id>)）
    running=$(docker exec flink-jobmanager flink list 2>/dev/null | grep -oP '(?<=: ).*(?= \([0-9a-f]{32}\)$)') || true
    has()    { echo "$running" | grep -qxF "$1"; }
    submit() {
        local out
        out=$(docker exec flink-jobmanager flink run -d -c "$1" "/opt/flink/usrlib/$NAME" 2>&1)
        if [ $? -eq 0 ]; then ok "作业 $2 已提交"; else echo "$out" | tail -8; fail "作业 $2 提交失败"; fi
    }

    has GpsCleanJob || submit com.bike.monitor.flink.clean.GpsCleanJob GpsCleanJob
    has FenceJob    || submit com.bike.monitor.flink.fence.FenceJob    FenceJob
    has H3AggJob    || submit com.bike.monitor.flink.heat.H3AggJob     H3AggJob
fi

echo ""
docker ps --format "table {{.Names}}\t{{.Status}}"
