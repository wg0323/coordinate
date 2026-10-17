package com.bike.monitor.flink.fence;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.common.dto.RideEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 围栏进出状态判定核心逻辑（与 Flink state 解耦，纯逻辑可单测）。
 *
 * <p>状态语义（inFence，bikeId 维度的 MapState）：
 * <ul>
 *   <li>absent = 在围栏外</li>
 *   <li>false  = 已进入但未确认（防抖窗口内）</li>
 *   <li>true   = 已确认在围栏内</li>
 * </ul>
 * pendingEnter 记录待确认围栏的进入位置（ENTER 事件携带）。
 * 防抖（题 10）：Enter 先登记 pending 并注册定时器，定时器触发时仍在内才发 ENTER；
 * 窗口内离开则静默撤销，解决 GPS 边界反复横跳误报。</p>
 */
public final class FenceStateDecider {

    /** 判定产生的事件（ENTER 携带进入位置；EXIT 的位置由调用方用当前点补齐） */
    public static final class Event {
        private final long fenceId;
        private final String type;
        private final GpsPoint entryPoint;

        public Event(long fenceId, String type, GpsPoint entryPoint) {
            this.fenceId = fenceId;
            this.type = type;
            this.entryPoint = entryPoint;
        }

        public long getFenceId() {
            return fenceId;
        }

        public String getType() {
            return type;
        }

        public GpsPoint getEntryPoint() {
            return entryPoint;
        }
    }

    public static final class Decision {
        public final List<Event> events = new ArrayList<>();
        /** 是否需要注册防抖确认定时器 */
        public boolean needTimer = false;
    }

    private FenceStateDecider() {
    }

    /**
     * 一次坐标判定：hits = 当前命中的围栏（fenceId → 命中点）。
     * 直接原地修改传入的 inFence / pendingEnter（调用方写回 Flink state）。
     */
    public static Decision decide(Map<Long, GpsPoint> hits, Map<Long, Boolean> inFence,
                                  Map<Long, GpsPoint> pendingEnter) {
        Decision decision = new Decision();
        for (Map.Entry<Long, GpsPoint> hit : hits.entrySet()) {
            if (!inFence.containsKey(hit.getKey())) {
                inFence.put(hit.getKey(), false);
                pendingEnter.put(hit.getKey(), hit.getValue());
                decision.needTimer = true;
            }
        }
        // 离开判定：已确认（true）→ EXIT；待确认（false）→ 静默撤销
        for (Long fenceId : new ArrayList<>(inFence.keySet())) {
            if (hits.containsKey(fenceId)) {
                continue;
            }
            if (Boolean.TRUE.equals(inFence.get(fenceId))) {
                decision.events.add(new Event(fenceId, RideEvent.EVENT_EXIT, null));
            }
            inFence.remove(fenceId);
            pendingEnter.remove(fenceId);
        }
        return decision;
    }

    /** 防抖定时器触发：仍在内的 pending 转正并发 ENTER（携带进入位置），其余静默清除 */
    public static List<Event> confirmPending(Map<Long, Boolean> inFence, Map<Long, GpsPoint> pendingEnter) {
        List<Event> events = new ArrayList<>();
        for (Map.Entry<Long, GpsPoint> e : new ArrayList<>(pendingEnter.entrySet())) {
            if (Boolean.FALSE.equals(inFence.get(e.getKey()))) {
                inFence.put(e.getKey(), true);
                events.add(new Event(e.getKey(), RideEvent.EVENT_ENTER, e.getValue()));
            }
        }
        pendingEnter.clear();
        return events;
    }
}
