package com.bike.monitor.server.service;

import com.bike.monitor.server.dto.DistrictCountDto;
import com.bike.monitor.server.dto.StatsDto;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 车辆统计口径测试：在线=5 分钟内有上报；骑行中=在线且未锁；空闲=在线且已锁。
 */
class StatsCalculatorTest {

    private static final long NOW = 1_000_000_000L;
    private static final long FRESH = NOW - 100_000;   // 100s 前，在 5min 窗口内
    private static final long STALE = NOW - 400_000;   // 400s 前，超出窗口

    @Test
    void should_return_zero_online_when_status_hash_is_empty() {
        StatsDto s = StatsCalculator.compute(84_840L, new HashMap<>(), NOW);
        assertEquals(84_840L, s.getTotal());
        assertEquals(0L, s.getOnline());
        assertEquals(0L, s.getRiding());
        assertEquals(0L, s.getIdle());
        assertEquals(84_840L, s.getOffline());
        assertEquals(0.0, s.getOnlineRate());
        assertTrue(s.getDistrictCounts().isEmpty());
    }

    @Test
    void should_classify_online_riding_idle_by_locked_and_ts() {
        Map<String, String> hash = new HashMap<>();
        hash.put("b1", "0,中心城区," + FRESH);  // 在线-骑行中
        hash.put("b2", "1,中心城区," + FRESH);  // 在线-空闲
        hash.put("b3", "0,通州," + STALE);      // 超窗-离线
        StatsDto s = StatsCalculator.compute(100L, hash, NOW);
        assertEquals(2L, s.getOnline());
        assertEquals(1L, s.getRiding());
        assertEquals(1L, s.getIdle());
        assertEquals(98L, s.getOffline());
        assertEquals(0.02, s.getOnlineRate(), 1e-9);
    }

    @Test
    void should_group_district_counts_and_skip_blank_or_malformed() {
        Map<String, String> hash = new HashMap<>();
        hash.put("b1", "0,中心城区," + FRESH);
        hash.put("b2", "1,中心城区," + FRESH);
        hash.put("b3", "0,通州," + FRESH);
        hash.put("b4", "1,," + FRESH);          // 区名空白 → 跳过分区但不影响总量
        hash.put("b5", "bad-value");            // 脏数据 → 跳过不抛异常
        StatsDto s = StatsCalculator.compute(100L, hash, NOW);
        assertEquals(4L, s.getOnline());        // b4 仍计入在线
        assertEquals(2, s.getDistrictCounts().size());
        DistrictCountDto xc = s.getDistrictCounts().stream()
                .filter(d -> d.getName().equals("中心城区")).findFirst().orElseThrow(IllegalStateException::new);
        assertEquals(2L, xc.getCount());
        assertEquals(1L, xc.getRiding());
        DistrictCountDto tz = s.getDistrictCounts().stream()
                .filter(d -> d.getName().equals("通州")).findFirst().orElseThrow(IllegalStateException::new);
        assertEquals(1L, tz.getCount());
        assertEquals(1L, tz.getRiding());
    }
}
