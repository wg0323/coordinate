package com.bike.monitor.server.service;

import com.bike.monitor.server.dto.DistrictCountDto;
import com.bike.monitor.server.dto.StatsDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 车辆统计口径（纯函数，便于单测）：
 * 在线 = 5 分钟内有上报；骑行中 = 在线且未锁车；空闲 = 在线且已锁车；离线 = 台账 - 在线。
 */
public final class StatsCalculator {

    /** 在线判定窗口：最近 5 分钟 */
    public static final long ONLINE_WINDOW_MS = 5 * 60 * 1000L;

    private StatsCalculator() {
    }

    /** 单车状态推导：在线未锁=riding，在线已锁=idle，其余（超窗/脏数据/null）=unknown */
    public static String statusOf(BikeStatusCodec.StatusEntry s, long nowMs) {
        if (s == null) {
            return "unknown";
        }
        if (nowMs - s.getTs() > ONLINE_WINDOW_MS || s.getTs() > nowMs + ONLINE_WINDOW_MS) {
            return "unknown";
        }
        return s.isLocked() ? "idle" : "riding";
    }

    public static StatsDto compute(long total, Map<String, String> statusHash, long nowMs) {
        long online = 0;
        long riding = 0;
        long idle = 0;
        Map<String, long[]> byDistrict = new HashMap<>();
        for (Map.Entry<String, String> e : statusHash.entrySet()) {
            BikeStatusCodec.StatusEntry s = BikeStatusCodec.decode(e.getValue());
            if (s == null) {
                continue;
            }
            if (nowMs - s.getTs() > ONLINE_WINDOW_MS || s.getTs() > nowMs + ONLINE_WINDOW_MS) {
                continue;
            }
            online++;
            if (s.isLocked()) {
                idle++;
            } else {
                riding++;
            }
            if (s.getDistrict() != null && !s.getDistrict().isEmpty()) {
                long[] agg = byDistrict.computeIfAbsent(s.getDistrict(), k -> new long[2]);
                agg[0]++;
                if (!s.isLocked()) {
                    agg[1]++;
                }
            }
        }
        List<DistrictCountDto> districtCounts = new ArrayList<>();
        for (Map.Entry<String, long[]> e : byDistrict.entrySet()) {
            districtCounts.add(new DistrictCountDto(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        districtCounts.sort(Comparator.comparingLong(DistrictCountDto::getCount).reversed());
        double onlineRate = total > 0 ? (double) online / total : 0.0;
        return new StatsDto(total, online, riding, idle, total - online, onlineRate, districtCounts);
    }
}
