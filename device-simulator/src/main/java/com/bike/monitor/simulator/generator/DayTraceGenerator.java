package com.bike.monitor.simulator.generator;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.simulator.roadnet.RoadNet;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 固定设备一天多段骑行：每段沿 A* 路径插值，段间停放心跳。
 * 所有点均在路网边上，ts 落在 [dayStartMs, dayStartMs + 24h) 内。
 */
public class DayTraceGenerator {

    private static final long REPORT_INTERVAL_MS = 5_000;
    private static final double REPORT_DISTANCE_M = 50;
    private static final long PARK_HEARTBEAT_MS = 600_000;

    private final RoadNet net;
    private final Random random;

    public DayTraceGenerator(RoadNet net, long seed) {
        this.net = net;
        this.random = new Random(seed);
    }

    public List<GpsPoint> generate(String bikeId, String district, long dayStartMs, int segments) {
        return generate(bikeId, district, dayStartMs, segments, 10000, 12000);
    }

    /** minSegM/targetSegM 供单测用小路网覆盖，默认 10km/12km */
    public List<GpsPoint> generate(String bikeId, String district, long dayStartMs, int segments,
                                   double minSegM, double targetSegM) {
        List<GpsPoint> points = new ArrayList<>();
        int cursor = random.nextInt(net.nodeCount());
        long t = dayStartMs + (8 + random.nextInt(2)) * 3600_000L;
        for (int s = 0; s < segments; s++) {
            RoadNet.Path path = pickRoute(cursor, minSegM, targetSegM);
            double speed = 3.5 + random.nextDouble();
            double nextMark = REPORT_DISTANCE_M;
            double dist = 0;
            while (dist < path.lengthM) {
                double[] pos = net.pointAt(path, dist);
                double instant = speed * (0.9 + random.nextDouble() * 0.2);
                points.add(point(bikeId, district, pos[0], pos[1], instant, (float) pos[2], false, t));
                double toMark = nextMark - dist;
                long timeToMark = (long) (toMark / Math.max(instant, 0.1) * 1000);
                long step = Math.min(REPORT_INTERVAL_MS, timeToMark);
                if (step < 1000) {
                    step = 1000;
                }
                t += step;
                dist += instant * step / 1000.0;
                while (dist >= nextMark) {
                    nextMark += REPORT_DISTANCE_M;
                }
            }
            t += 2000; // 到站后停车耗时
            cursor = path.nodes[path.nodes.length - 1];
            long nextStart = t + (30 + random.nextInt(61)) * 60_000L;
            for (long parkTs = t + PARK_HEARTBEAT_MS; parkTs < nextStart; parkTs += PARK_HEARTBEAT_MS) {
                double[] end = net.pointAt(path, path.lengthM);
                points.add(point(bikeId, district, end[0], end[1], 0, (float) end[2], true, parkTs));
            }
            t = nextStart;
        }
        return points;
    }

    private RoadNet.Path pickRoute(int from, double minSegM, double targetSegM) {
        int n = net.nodeCount();
        RoadNet.Path best = null;
        double bestScore = Double.MAX_VALUE;
        for (int attempt = 0; attempt < 6 && best == null; attempt++) {
            for (int i = 0; i < 12; i++) {
                int to = random.nextInt(n);
                if (to == from) {
                    continue;
                }
                RoadNet.Path p = net.shortestPath(from, to);
                if (p == null || p.lengthM < minSegM) {
                    continue;
                }
                double score = Math.abs(p.lengthM - targetSegM);
                if (score < bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("路网内找不到足够长的骑行路径（minSegM=" + minSegM + "）");
        }
        return best;
    }

    private GpsPoint point(String bikeId, String district, double lng, double lat,
                           double speed, float direction, boolean locked, long ts) {
        return GpsPoint.builder()
                .bikeId(bikeId)
                .district(district)
                .lng(lng)
                .lat(lat)
                .speed(speed)
                .direction(direction)
                .locked(locked)
                .ts(ts)
                .build();
    }
}
