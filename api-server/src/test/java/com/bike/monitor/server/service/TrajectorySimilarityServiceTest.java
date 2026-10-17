package com.bike.monitor.server.service;

import com.bike.monitor.server.dto.TracePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrajectorySimilarityServiceTest {

    private final TrajectorySimilarityService service = new TrajectorySimilarityService();

    private static TracePoint p(double lng, double lat) {
        return new TracePoint(0L, lng, lat, 0);
    }

    /** 沿经线向北的轨迹 */
    private static List<TracePoint> northRoute(double lng, double latStart, double latEnd, int steps) {
        List<TracePoint> route = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            route.add(p(lng, latStart + (latEnd - latStart) * i / (steps - 1.0)));
        }
        return route;
    }

    @Test
    void should_identical_trajectory_score_best_when_compare_routes() {
        List<TracePoint> a = northRoute(116.40, 39.90, 39.92, 50);
        // 完全相同的轨迹
        double dtwSame = service.dtw(a, a);
        double dtwOther = service.dtw(a, northRoute(116.42, 39.90, 39.92, 50));
        assertTrue(dtwSame < dtwOther);
        assertTrue(service.frechet(a, a) < service.frechet(a, northRoute(116.42, 39.90, 39.92, 50)));
    }

    @Test
    void should_high_lcss_when_offset_noise_within_epsilon() {
        List<TracePoint> a = northRoute(116.40, 39.90, 39.92, 50);
        // 同路线 + 每点 30m 内噪声（纬度 0.00027° ≈ 30m）
        List<TracePoint> b = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            b.add(p(a.get(i).getLng() + 0.0001, a.get(i).getLat() + 0.0001));
        }
        double lcss = service.lcss(a, b, 200, 100);
        assertTrue(lcss > 0.9, "lcss=" + lcss);
        // EDR：阈值内全匹配 → 编辑距离 0
        assertEquals(0, service.edr(a, b, 200));
    }

    @Test
    void should_low_similarity_when_routes_diverge() {
        List<TracePoint> a = northRoute(116.40, 39.90, 39.92, 50);
        // 垂直方向（向东）的路线
        List<TracePoint> c = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            c.add(p(116.40 + 0.02 * i / 49.0, 39.90));
        }
        double frechet = service.frechet(a, c);
        // 两路线中点相距 ~1.7km，Fréchet 应显著大于该值的一半
        assertTrue(frechet > 800, "frechet=" + frechet);
        assertTrue(service.lcss(a, c, 200, 100) < 0.2);
    }

    @Test
    void should_resample_free_dtw_handle_different_density() {
        // 同一条路线，一条 50 点、一条 100 点（采样密度不同）
        List<TracePoint> a = northRoute(116.40, 39.90, 39.92, 50);
        List<TracePoint> b = northRoute(116.40, 39.90, 39.92, 100);
        // DTW 专治采样频率不同：同路线归一化值应接近 0
        double dtw = service.dtw(a, b);
        assertTrue(dtw < 50, "dtw=" + dtw);
    }
}
