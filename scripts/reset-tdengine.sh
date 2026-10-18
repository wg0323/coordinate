#!/bin/bash
# TDengine 元数据一键重置（在虚拟机 /opt 下、与 docker-compose.yml 同目录执行）
#
# 背景：dnode 元数据里记录的 FQDN 与当前容器 hostname 不一致时，taosd 启动会报
#       failed to resolve sync addr / Invalid host name 并退出。
#       若确认 TDengine 历史数据无用，本脚本会清空元数据并按当前 compose 配置重建。
#
# 用法：
#   ./reset-tdengine.sh                 # 清空元数据并重建（历史数据不可恢复）
#   ./reset-tdengine.sh --restart-flink # 重建成功后重启 taskmanager，触发作业重新拉起
#   ./reset-tdengine.sh --yes           # 跳过“已停模拟器”确认
#
# 注意：执行前必须先停止 Windows 上的模拟器，避免 TDengine 恢复后再次被百万 TPS 打挂。
set -euo pipefail
cd "$(dirname "$0")"

ASSUME_YES=false
RESTART_FLINK=false
for a in "$@"; do
    case "$a" in
        --restart-flink) RESTART_FLINK=true ;;
        --yes|-y) ASSUME_YES=true ;;
        -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "未知参数: $a"; exit 1 ;;
    esac
done

ok()   { echo "[OK] $1"; }
info() { echo "[..] $1"; }
fail() { echo "[FAIL] $1"; exit 1; }

[ -f docker-compose.yml ] || fail "当前目录没有 docker-compose.yml"
docker inspect tdengine >/dev/null 2>&1 || fail "未找到 tdengine 容器"

if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
else
    COMPOSE="docker-compose"
fi

# 清空后会按 compose 的 hostname/TAOS_FQDN 重新初始化，必须与预期一致
grep -Eq '^[[:space:]]*hostname:[[:space:]]*"?tdengine"?[[:space:]]*$' docker-compose.yml \
    || fail "docker-compose.yml 缺少 hostname: tdengine，先补上再执行"
grep -q 'TAOS_FQDN=tdengine' docker-compose.yml \
    || fail "docker-compose.yml 缺少 TAOS_FQDN=tdengine，先补上再执行"

# 以容器实际挂载路径为准，避免猜错目录
DATA_DIR=$(docker inspect tdengine --format '{{range .Mounts}}{{if eq .Destination "/var/lib/taos"}}{{.Source}}{{end}}{{end}}')
LOG_DIR=$(docker inspect tdengine --format '{{range .Mounts}}{{if eq .Destination "/var/log/taos"}}{{.Source}}{{end}}{{end}}')
[ -n "$DATA_DIR" ] || fail "未找到 /var/lib/taos 挂载目录"
[ -n "$LOG_DIR" ] || LOG_DIR=""

# 防呆：只允许操作 ……/tdengine/data（含 .bak 后缀）
REAL_DATA=$(readlink -f "$DATA_DIR")
case "$REAL_DATA" in
    */tdengine/data|*/tdengine/data.bak-*) ;;
    *) fail "数据目录不符合预期，拒绝操作: $REAL_DATA" ;;
esac
case "$LOG_DIR" in
    */tdengine/log) ;;
    *) LOG_DIR="" ;;
esac

info "容器: tdengine"
info "数据目录: $REAL_DATA"
info "日志目录: ${LOG_DIR:-<跳过>}"

if [ "$ASSUME_YES" != true ]; then
    echo "请先停止 Windows 上的模拟器，再继续。"
    read -r -p "已停止模拟器？输入 yes 继续: " ans
    [ "$ans" = "yes" ] || fail "已取消"
fi

# 1. 停 TDengine
info "停止 tdengine ..."
$COMPOSE stop tdengine

# 2. 清空数据（历史数据不再保留）
info "清空旧数据 ..."
rm -rf "${REAL_DATA:?}/"*
rm -rf "${REAL_DATA:?}/".[!.]* 2>/dev/null || true
[ -n "$LOG_DIR" ] && rm -rf "${LOG_DIR:?}/"* 2>/dev/null || true

# 3. 重建容器
info "重建 tdengine ..."
$COMPOSE up -d --force-recreate tdengine

# 4. 等待就绪
info "等待 TDengine 就绪（最多 60s）..."
READY=false
for i in $(seq 1 30); do
    if docker exec tdengine taos -s "show databases" >/tmp/tdengine_ready.out 2>&1; then
        READY=true
        break
    fi
    sleep 2
done
if ! $READY; then
    docker logs tdengine --tail 60 || true
    fail "TDengine 未就绪，请按上方日志排查"
fi
ok "TDengine 已就绪"
head -5 /tmp/tdengine_ready.out 2>/dev/null || true
rm -f /tmp/tdengine_ready.out

# 5. 可选：重启 taskmanager，让 Flink 作业立即重新拉起
if $RESTART_FLINK; then
    info "重启 flink-taskmanager ..."
    docker restart flink-taskmanager >/dev/null
fi

echo
ok "完成。接下来："
echo "  1) 打开 http://<虚拟机IP>:8081 ，等 GpsCleanJob 的 checkpoint 恢复成功"
echo "  2) Windows 上以 --vehicle-ratio=0.1 启动模拟器"
echo "  3) 30~60s 后检查 /api/fence/stats：非空围栏 ~225、栏内 ~3000 辆"
