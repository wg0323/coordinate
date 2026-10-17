#!/bin/bash
# 虚拟机一键启动 + Flink 作业部署脚本（配合同目录 docker-compose.yml，与 .env 一起使用）
#
# 日常启动（不重新部署作业）:
#   ./vm-start.sh
#
# 重新打包后部署新作业 jar（在 Windows 上 mvn package 后 scp 到虚拟机）:
#   ./vm-start.sh /opt/flink-jobs/flink-jobs-1.0.0-SNAPSHOT.jar --fresh
#
# 说明:
#   --fresh    先取消所有在跑作业，再部署并提交新 jar（jar 更新后必须用这个）
#   不带 --fresh 时，仅拷贝 jar 并提交"缺失"的作业，不影响在跑作业

set -u
cd "$(dirname "$0")"

JAR=""
FRESH=false
for a in "$@"; do
    case "$a" in
        --fresh) FRESH=true ;;
        -*) echo "未知参数: $a（支持 --fresh）"; echo "用法: ./vm-start.sh [jar路径] [--fresh]"; exit 1 ;;
        *) JAR="$a" ;;
    esac
done

ok()   { echo "[OK] $1"; }
fail() { echo "[FAIL] $1"; exit 1; }

# ---------- 1. 启动容器 ----------
docker compose -f docker-compose.yml up -d || fail "docker compose 启动失败"

# ---------- 2. Kafka 就绪自愈（ZK 残留节点是惯犯） ----------
kafka_ready() { docker exec kafka kafka-broker-api-versions.sh --bootstrap-server kafka:9092 >/dev/null 2>&1; }
for i in $(seq 1 30); do kafka_ready && break; sleep 2; done
if ! kafka_ready; then
    echo "[WARN] Kafka 未就绪，清理 ZK 残留节点 /brokers/ids/1 后重试"
    docker exec zookeeper zkCli.sh rmr /brokers/ids/1 >/dev/null 2>&1
    docker restart kafka >/dev/null
    for i in $(seq 1 30); do kafka_ready && break; sleep 2; done
    kafka_ready || { docker logs kafka --tail 30; fail "Kafka 重试后仍未就绪"; }
fi
ok "中间件已启动 (Redis/MongoDB/TDengine/ZK/Kafka)"

# ---------- 3. Flink REST 就绪 ----------
for i in $(seq 1 30); do curl -sf http://localhost:8081/overview >/dev/null && break; sleep 2; done
curl -sf http://localhost:8081/overview >/dev/null || { docker logs flink-jobmanager --tail 30; fail "Flink REST 未就绪"; }
ok "Flink 集群已启动 (UI: http://localhost:8081)"

# ---------- 4. 作业部署 ----------
if [ -z "$JAR" ]; then
    echo "[SKIP] 未指定 jar，跳过作业部署。重新打包后: ./vm-start.sh <jar路径> --fresh"
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
