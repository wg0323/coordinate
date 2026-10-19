package com.bike.monitor.simulator.generator;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.simulator.roadnet.RoadNet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DayTraceGeneratorTest {

    private static final long DAY = 24L * 3600 * 1000;

    private List<GpsPoint> generate() throws Exception {
        RoadNet net = RoadNet.loadFromClasspath("roadnet/test-grid.json");
        // 测试网格较小，用 1km/2km 段长覆盖生成逻辑
        return new DayTraceGenerator(net, 42L).generate("BMXC000123", "中心城区", 0L, 5, 1000, 2000);
    }

    @Test
    void should_generate_five_segments_with_monotonic_ts() throws Exception {
        List<GpsPoint> points = generate();
        assertTrue(points.size() > 200, "points=" + points.size());
        for (int i = 1; i < points.size(); i++) {
            assertTrue(points.get(i).getTs() > points.get(i - 1).getTs());
        }
        assertEquals("BMXC000123", points.get(0).getBikeId());
        assertEquals("中心城区", points.get(0).getDistrict());
        // 5 段行程应有 5 个停放段（每段结束后都有停放心跳）
        int parkRuns = 0;
        boolean inPark = false;
        for (GpsPoint p : points) {
            boolean park = p.getSpeed() == 0;
            if (park && !inPark) {
                parkRuns++;
            }
            inPark = park;
        }
        assertEquals(5, parkRuns);
    }

    @Test
    void should_mark_parked_points_locked_and_keep_ts_within_day() throws Exception {
        List<GpsPoint> points = generate();
        int parked = 0;
        for (GpsPoint p : points) {
            if (p.getSpeed() == 0) {
                parked++;
                assertTrue(p.isLocked());
                assertTrue(p.getTs() < DAY);
            } else {
                assertFalse(p.isLocked());
                assertTrue(p.getTs() < DAY);
            }
        }
        assertTrue(parked > 0, "至少应有停放心跳点");
    }

    @Test
    void should_keep_every_sampled_point_on_road() throws Exception {
        RoadNet net = RoadNet.loadFromClasspath("roadnet/test-grid.json");
        List<GpsPoint> points = new DayTraceGenerator(net, 42L)
                .generate("BMXC000123", "中心城区", 0L, 5, 1000, 2000);
        for (int i = 0; i < points.size(); i += 20) {
            GpsPoint p = points.get(i);
            double dist = net.nearestEdgeDistanceM(p.getLng(), p.getLat());
            assertTrue(dist < 0.5, "point " + i + " distance=" + dist);
        }
    }

    @Test
    void should_be_deterministic_for_same_seed() throws Exception {
        List<GpsPoint> a = generate();
        List<GpsPoint> b = generate();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).getTs(), b.get(i).getTs());
            assertEquals(a.get(i).getLng(), b.get(i).getLng());
            assertEquals(a.get(i).getLat(), b.get(i).getLat());
        }
    }
}
