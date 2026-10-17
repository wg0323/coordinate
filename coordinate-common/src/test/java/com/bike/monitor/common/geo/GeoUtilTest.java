package com.bike.monitor.common.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoUtilTest {

    @Test
    void should_about_111km_when_one_degree_at_equator() {
        // R=6371000 时赤道 1 度 ≈ 111194.93m，允许 1% 误差
        double d = GeoUtil.haversine(0, 0, 1, 0);
        assertEquals(111194.93, d, 1112);
    }

    @Test
    void should_0_when_same_point() {
        assertEquals(0.0, GeoUtil.haversine(116.4, 39.9, 116.4, 39.9), 1e-9);
    }

    @Test
    void should_1_to_1_5km_when_tiananmen_to_wangfujing() {
        double d = GeoUtil.haversine(116.397428, 39.90923, 116.410877, 39.904693);
        assertTrue(d > 1000 && d < 1500, "d=" + d);
    }

    @Test
    void should_speed_when_distance_over_time() {
        // 1111.95m / 100s = 11.1195 m/s ≈ 40 km/h
        assertEquals(11.1195, GeoUtil.speedMps(1111.95, 100), 1e-3);
    }
}
