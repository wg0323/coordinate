package com.bike.monitor.server.service;

import com.bike.monitor.server.dto.TracePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceSimplifyTest {

    private final TraceSimplifyService service = new TraceSimplifyService();

    private static TracePoint p(double lng, double lat) {
        return new TracePoint(0L, lng, lat, 0);
    }

    /** 直线轨迹（中间点全共线）+ 一个拐点 */
    private List<TracePoint> routeWithCorner() {
        List<TracePoint> route = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            route.add(p(116.40 + 0.001 * i, 39.90));
        }
        route.add(p(116.41, 39.905)); // 拐点
        for (int i = 1; i < 10; i++) {
            route.add(p(116.41 + 0.001 * i, 39.905));
        }
        return route;
    }

    @Test
    void should_keep_corners_and_drop_collinear_when_simplify() {
        List<TracePoint> raw = routeWithCorner();
        List<TracePoint> simplified = service.douglasPeucker(raw, 0.00005);
        // 共线段可压缩，但两个端点 + 拐点必须保留
        assertTrue(simplified.size() < raw.size());
        assertEquals(raw.get(0), simplified.get(0));
        assertEquals(raw.get(raw.size() - 1), simplified.get(simplified.size() - 1));
        assertTrue(simplified.contains(p(116.41, 39.905)));
    }

    @Test
    void should_keep_all_when_tolerance_zero() {
        List<TracePoint> raw = routeWithCorner();
        assertEquals(raw.size(), service.douglasPeucker(raw, 0).size());
    }

    @Test
    void should_match_jts_result_count_when_compare() {
        List<TracePoint> raw = routeWithCorner();
        int self = service.douglasPeucker(raw, 0.0001).size();
        int jts = service.jtsDouglasPeucker(raw, 0.0001).size();
        // 自实现与 JTS 在同阈值下的压缩程度应接近（点数差异 ≤ 20%）
        assertTrue(Math.abs(self - jts) <= Math.max(2, jts * 0.2), "self=" + self + " jts=" + jts);
    }
}
