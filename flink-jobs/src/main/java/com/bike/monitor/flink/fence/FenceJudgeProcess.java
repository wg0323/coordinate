package com.bike.monitor.flink.fence;

import com.bike.monitor.common.dto.FenceRule;
import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.common.dto.RideEvent;
import com.bike.monitor.flink.util.JsonUtil;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 围栏判定核心算子（题 8/9/10）。
 *
 * <ul>
 *   <li>题 9：BroadcastState 全量围栏 → 每 subtask 惰性重建 STRtree（MBR 粗筛 O(logN)）→ JTS covers 精筛</li>
 *   <li>题 10：keyBy(bikeId) + MapState 进出状态 + 事件时间定时器防抖（10s 模拟时间内持续在内才发 ENTER）</li>
 *   <li>围栏删除：广播流 DELETE 事件同步清理各 key 的 MapState 条目（applyToKeyedState），避免状态泄漏；
 *       MapState 另配 StateTtlConfig 兜底</li>
 * </ul>
 *
 * <p>防抖用事件时间定时器（以上报 ts 为基准的模拟时间），与 speedFactor 时间加速自洽；
 * 依赖上游 watermark（见 FenceJob：GPS 流打事件时间水印，config 流发 MAX 水印不拖后腿）。</p>
 */
public class FenceJudgeProcess extends KeyedBroadcastProcessFunction<String, GpsPoint, String, RideEvent> {

    /** 广播状态值存原始 JSON（String 在广播边序列化最稳健），解析在本算子内完成 */
    public static final MapStateDescriptor<Long, String> RULE_DESCRIPTOR =
            new MapStateDescriptor<>("fence-rules-json", TypeInformation.of(Long.class),
                    TypeInformation.of(String.class));

    private static final long DEBOUNCE_MS = 10_000;

    private static final StateTtlConfig STATE_TTL = StateTtlConfig.newBuilder(Time.hours(24))
            .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
            .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
            .build();

    private static MapStateDescriptor<Long, Boolean> booleanMapState(String name) {
        MapStateDescriptor<Long, Boolean> desc =
                new MapStateDescriptor<>(name, TypeInformation.of(Long.class), TypeInformation.of(Boolean.class));
        desc.enableTimeToLive(STATE_TTL);
        return desc;
    }

    private static MapStateDescriptor<Long, GpsPoint> entryPointMapState(String name) {
        MapStateDescriptor<Long, GpsPoint> desc =
                new MapStateDescriptor<>(name, TypeInformation.of(Long.class), TypeInformation.of(GpsPoint.class));
        desc.enableTimeToLive(STATE_TTL);
        return desc;
    }

    private final GeometryFactory geometryFactory = new GeometryFactory();

    /** 每 subtask 本地：fenceId → 多边形（精筛用） */
    private transient Map<Long, Geometry> polygons;
    /** 每 subtask 本地：MBR 空间索引（粗筛用），dirty 时整体重建 */
    private transient STRtree index;
    /** 脏标记：围栏配置变更后整体重建（transient 字段在 open() 重置） */
    private transient volatile boolean dirty;

    /** bikeId → (fenceId → 状态)：true=确认在内，false=待确认，absent=在外（TTL 兜底） */
    private transient MapState<Long, Boolean> inFence;
    /** 待确认进入的围栏 → 进入位置（ENTER 事件携带） */
    private transient MapState<Long, GpsPoint> pendingEnter;
    /** 本 key 是否已注册防抖定时器 */
    private transient ValueState<Boolean> timerActive;

    @Override
    public void open(Configuration parameters) {
        // transient 字段经 Flink 序列化后不保留初始化值，统一在 open() 重置
        dirty = true;
        inFence = getRuntimeContext().getMapState(booleanMapState("in-fence"));
        pendingEnter = getRuntimeContext().getMapState(entryPointMapState("pending-enter"));
        timerActive = getRuntimeContext().getState(new ValueStateDescriptor<>("debounce-timer", Boolean.class));
    }

    @Override
    public void processElement(GpsPoint point, ReadOnlyContext ctx, Collector<RideEvent> out) throws Exception {
        if (dirty) {
            rebuildIndex(ctx);
        }
        Point p = geometryFactory.createPoint(new Coordinate(point.getLng(), point.getLat()));
        // 粗筛：MBR 命中候选（O(logN)），精筛：JTS covers（边界视为在内）
        Map<Long, GpsPoint> hits = new HashMap<>();
        if (index != null) {
            for (Object fenceId : index.query(new Envelope(p.getCoordinate()))) {
                Geometry polygon = polygons.get(fenceId);
                if (polygon != null && polygon.covers(p)) {
                    hits.put((Long) fenceId, point);
                }
            }
        }

        // 物化 keyed state → 纯逻辑判定 → 写回
        Map<Long, Boolean> inFenceSnapshot = materialize(inFence);
        Map<Long, GpsPoint> pendingSnapshot = materializePoints(pendingEnter);

        FenceStateDecider.Decision decision =
                FenceStateDecider.decide(hits, inFenceSnapshot, pendingSnapshot);
        for (FenceStateDecider.Event event : decision.events) {
            out.collect(buildEvent(event, point, ctx));
        }
        writeBack(inFenceSnapshot, pendingSnapshot);

        if (decision.needTimer && !Boolean.TRUE.equals(timerActive.value())) {
            ctx.timerService().registerEventTimeTimer(point.getTs() + DEBOUNCE_MS);
            timerActive.update(true);
        }
    }

    /** 防抖定时器：仍在内的 pending 转正发 ENTER（携带进入位置） */
    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<RideEvent> out) throws Exception {
        Map<Long, Boolean> inFenceSnapshot = materialize(inFence);
        Map<Long, GpsPoint> pendingSnapshot = materializePoints(pendingEnter);
        List<FenceStateDecider.Event> events =
                FenceStateDecider.confirmPending(inFenceSnapshot, pendingSnapshot);
        for (FenceStateDecider.Event event : events) {
            RideEvent rideEvent = new RideEvent();
            rideEvent.setFenceId(event.getFenceId());
            rideEvent.setFenceName(fenceName(ctx, event.getFenceId()));
            rideEvent.setEvent(event.getType());
            GpsPoint entry = event.getEntryPoint();
            if (entry != null) {
                rideEvent.setBikeId(entry.getBikeId());
                rideEvent.setLng(entry.getLng());
                rideEvent.setLat(entry.getLat());
            }
            rideEvent.setTs(timestamp - DEBOUNCE_MS);
            out.collect(rideEvent);
        }
        writeBack(inFenceSnapshot, pendingSnapshot);
        timerActive.update(false);
    }

    @Override
    public void processBroadcastElement(String ruleJson, Context ctx, Collector<RideEvent> out) throws Exception {
        FenceRule rule = JsonUtil.parseFenceRule(ruleJson);
        if (rule == null || rule.getFenceId() == null) {
            return;
        }
        org.apache.flink.api.common.state.BroadcastState<Long, String> state =
                ctx.getBroadcastState(RULE_DESCRIPTOR);
        if (FenceRule.OP_DELETE.equals(rule.getOp())) {
            state.remove(rule.getFenceId());
            // 删除围栏：同步清理所有车辆的进出状态与 pending 条目（避免状态泄漏）
            ctx.applyToKeyedState(booleanMapState("in-fence"), (key, state1) -> state1.remove(rule.getFenceId()));
            ctx.applyToKeyedState(entryPointMapState("pending-enter"), (key, state1) -> state1.remove(rule.getFenceId()));
        } else {
            state.put(rule.getFenceId(), ruleJson);
        }
        dirty = true;
    }

    private void rebuildIndex(ReadOnlyContext ctx) throws Exception {
        polygons = new HashMap<>();
        STRtree tree = new STRtree();
        for (Map.Entry<Long, String> e : ctx.getBroadcastState(RULE_DESCRIPTOR).immutableEntries()) {
            FenceRule rule = JsonUtil.parseFenceRule(e.getValue());
            Geometry polygon = rule == null ? null : toPolygon(rule);
            if (polygon != null) {
                polygons.put(e.getKey(), polygon);
                tree.insert(polygon.getEnvelopeInternal(), e.getKey());
            }
        }
        tree.build();
        index = tree;
        dirty = false;
    }

    private Geometry toPolygon(FenceRule rule) {
        if (rule.getCoordinates() == null || rule.getCoordinates().length == 0
                || rule.getCoordinates()[0].length < 3) {
            return null;
        }
        double[][] ring = rule.getCoordinates()[0];
        Coordinate[] coords = new Coordinate[ring.length + 1];
        for (int i = 0; i < ring.length; i++) {
            coords[i] = new Coordinate(ring[i][0], ring[i][1]);
        }
        coords[ring.length] = coords[0];
        return geometryFactory.createPolygon(coords);
    }

    private RideEvent buildEvent(FenceStateDecider.Event event, GpsPoint current, ReadOnlyContext ctx)
            throws Exception {
        RideEvent rideEvent = new RideEvent();
        rideEvent.setFenceId(event.getFenceId());
        rideEvent.setFenceName(fenceName(ctx, event.getFenceId()));
        rideEvent.setEvent(event.getType());
        // ENTER 用进入位置；EXIT 用当前位置
        GpsPoint position = event.getEntryPoint() != null ? event.getEntryPoint() : current;
        rideEvent.setBikeId(position.getBikeId());
        rideEvent.setLng(position.getLng());
        rideEvent.setLat(position.getLat());
        rideEvent.setTs(position.getTs());
        return rideEvent;
    }

    private String fenceName(ReadOnlyContext ctx, Long fenceId) throws Exception {
        String json = ctx.getBroadcastState(RULE_DESCRIPTOR).get(fenceId);
        FenceRule rule = json == null ? null : JsonUtil.parseFenceRule(json);
        return rule == null ? null : rule.getName();
    }

    private Map<Long, Boolean> materialize(MapState<Long, Boolean> state) throws Exception {
        Map<Long, Boolean> snapshot = new HashMap<>();
        for (Map.Entry<Long, Boolean> e : state.entries()) {
            snapshot.put(e.getKey(), e.getValue());
        }
        return snapshot;
    }

    private Map<Long, GpsPoint> materializePoints(MapState<Long, GpsPoint> state) throws Exception {
        Map<Long, GpsPoint> snapshot = new HashMap<>();
        for (Map.Entry<Long, GpsPoint> e : state.entries()) {
            snapshot.put(e.getKey(), e.getValue());
        }
        return snapshot;
    }

    private void writeBack(Map<Long, Boolean> inFenceSnapshot, Map<Long, GpsPoint> pendingSnapshot)
            throws Exception {
        inFence.clear();
        for (Map.Entry<Long, Boolean> e : inFenceSnapshot.entrySet()) {
            inFence.put(e.getKey(), e.getValue());
        }
        pendingEnter.clear();
        for (Map.Entry<Long, GpsPoint> e : pendingSnapshot.entrySet()) {
            pendingEnter.put(e.getKey(), e.getValue());
        }
    }
}
