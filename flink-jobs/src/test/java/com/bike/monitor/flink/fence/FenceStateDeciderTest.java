package com.bike.monitor.flink.fence;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.common.dto.RideEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FenceStateDeciderTest {

    private static Set<Long> set(Long... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static Map<Long, GpsPoint> hits(Long... fenceIds) {
        Map<Long, GpsPoint> map = new HashMap<>();
        for (Long id : fenceIds) {
            map.put(id, GpsPoint.builder().bikeId("BMX").lng(116.4).lat(39.9).ts(1L).build());
        }
        return map;
    }

    @Test
    void should_register_pending_without_event_when_first_enter() {
        Map<Long, Boolean> inFence = new HashMap<>();
        Map<Long, GpsPoint> pending = new HashMap<>();

        FenceStateDecider.Decision d = FenceStateDecider.decide(hits(1L), inFence, pending);

        assertTrue(d.events.isEmpty());
        assertTrue(d.needTimer);
        assertEquals(false, inFence.get(1L));
        assertTrue(pending.containsKey(1L));
    }

    @Test
    void should_confirm_enter_when_timer_fires_and_still_inside() {
        Map<Long, Boolean> inFence = new HashMap<>();
        Map<Long, GpsPoint> pending = new HashMap<>();
        FenceStateDecider.decide(hits(1L), inFence, pending);

        List<FenceStateDecider.Event> events = FenceStateDecider.confirmPending(inFence, pending);

        assertEquals(1, events.size());
        assertEquals(RideEvent.EVENT_ENTER, events.get(0).getType());
        assertEquals(1L, events.get(0).getFenceId());
        assertEquals("BMX", events.get(0).getEntryPoint().getBikeId());
        assertEquals(true, inFence.get(1L));
        assertTrue(pending.isEmpty());
    }

    @Test
    void should_silent_cancel_when_left_before_confirm() {
        // 边界横跳场景：进入→离开→定时器触发，全程不产生任何事件
        Map<Long, Boolean> inFence = new HashMap<>();
        Map<Long, GpsPoint> pending = new HashMap<>();
        FenceStateDecider.decide(hits(1L), inFence, pending);
        FenceStateDecider.Decision d = FenceStateDecider.decide(hits(), inFence, pending);

        assertTrue(d.events.isEmpty());
        assertFalse(inFence.containsKey(1L));
        assertTrue(pending.isEmpty());

        List<FenceStateDecider.Event> events = FenceStateDecider.confirmPending(inFence, pending);
        assertTrue(events.isEmpty());
    }

    @Test
    void should_emit_exit_when_confirmed_fence_left() {
        Map<Long, Boolean> inFence = new HashMap<>();
        Map<Long, GpsPoint> pending = new HashMap<>();
        FenceStateDecider.decide(hits(1L), inFence, pending);
        FenceStateDecider.confirmPending(inFence, pending);

        FenceStateDecider.Decision d = FenceStateDecider.decide(hits(), inFence, pending);

        assertEquals(1, d.events.size());
        assertEquals(RideEvent.EVENT_EXIT, d.events.get(0).getType());
        assertFalse(inFence.containsKey(1L));
    }

    @Test
    void should_emit_enter_again_after_exit_cycle() {
        Map<Long, Boolean> inFence = new HashMap<>();
        Map<Long, GpsPoint> pending = new HashMap<>();
        // 完整一轮：进→确认→出
        FenceStateDecider.decide(hits(1L), inFence, pending);
        FenceStateDecider.confirmPending(inFence, pending);
        FenceStateDecider.decide(hits(), inFence, pending);
        // 再进：重新走防抖
        FenceStateDecider.Decision d = FenceStateDecider.decide(hits(1L), inFence, pending);
        assertTrue(d.needTimer);
        assertEquals(false, inFence.get(1L));
        List<FenceStateDecider.Event> events = FenceStateDecider.confirmPending(inFence, pending);
        assertEquals(1, events.size());
        assertEquals(RideEvent.EVENT_ENTER, events.get(0).getType());
    }

    @Test
    void should_handle_multi_fence_mixed_state() {
        Map<Long, Boolean> inFence = new HashMap<>();
        Map<Long, GpsPoint> pending = new HashMap<>();
        // 围栏 2 已确认在内；随后进入围栏 3（2 仍命中，待确认）
        FenceStateDecider.decide(hits(2L), inFence, pending);
        FenceStateDecider.confirmPending(inFence, pending);
        FenceStateDecider.decide(hits(2L, 3L), inFence, pending);

        // 当前命中：新增围栏 1，离开围栏 2（已确认→EXIT）、围栏 3（待确认→静默撤销）
        FenceStateDecider.Decision d = FenceStateDecider.decide(hits(1L), inFence, pending);

        assertEquals(1, d.events.size());
        assertEquals(RideEvent.EVENT_EXIT, d.events.get(0).getType());
        assertEquals(2L, d.events.get(0).getFenceId());
        assertTrue(d.needTimer);
        assertEquals(false, inFence.get(1L));
        // 围栏 3 离开且未确认 → 静默移除，不残留状态
        assertFalse(inFence.containsKey(3L));
    }
}
