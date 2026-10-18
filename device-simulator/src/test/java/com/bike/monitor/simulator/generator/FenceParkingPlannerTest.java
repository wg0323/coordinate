package com.bike.monitor.simulator.generator;

import com.bike.monitor.common.geo.RayCasting;
import com.bike.monitor.simulator.model.Bike;
import com.bike.monitor.simulator.model.District;
import com.bike.monitor.simulator.model.FenceSite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FenceParkingPlannerTest {

    private static final double LNG = 116.40;
    private static final double LAT = 39.90;

    @Test
    void should_assign_target_count_and_keep_every_home_inside_fences() {
        List<Bike> fleet = fleet(100);
        List<FenceSite> fences = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            fences.add(new FenceSite((long) i, "[停放50] 测试围栏" + i,
                    rect(LNG + i * 0.005, LAT, 0.001, 0.001), 50));
        }

        int assigned = new FenceParkingPlanner(42L).assign(fleet, fences, 100);

        // 目标停放 100 辆 → 需迁入 ceil(100/0.64)=157 辆，但车队只有 100 辆
        assertEquals(100, assigned);
        for (Bike bike : fleet) {
            assertTrue(insideAny(fences, bike.getHomeLng(), bike.getHomeLat()));
        }
        // 每栏容量 50 → 填充 20 辆
        assertEquals(20, countIn(fences.get(0), fleet));
    }

    @Test
    void should_keep_points_inside_thin_fence() {
        List<Bike> fleet = fleet(20);
        // 约 85m × 2.2m 的细长围栏，对应地图上路边长条
        FenceSite thin = new FenceSite(1L, "[停放20] 细长围栏", rect(LNG, LAT, 0.001, 0.00002), 20);

        int assigned = new FenceParkingPlanner(7L).assign(fleet, List.of(thin), 10);

        assertEquals(10, assigned);
        for (int i = 0; i < assigned; i++) {
            assertTrue(RayCasting.contains(thin.getRing(), fleet.get(i).getHomeLng(), fleet.get(i).getHomeLat()));
        }
    }

    @Test
    void should_do_nothing_when_no_capacity_fence() {
        List<Bike> fleet = fleet(10);
        double lng = fleet.get(0).getHomeLng();
        double lat = fleet.get(0).getHomeLat();
        List<FenceSite> fences = List.of(
                new FenceSite(1L, "[禁停0] 禁停区", rect(LNG, LAT, 0.001, 0.001), null),
                new FenceSite(2L, "[停放5] 小容量围栏", rect(LNG, LAT, 0.001, 0.001), 5));

        int assigned = new FenceParkingPlanner(42L).assign(fleet, fences, 10);

        assertEquals(0, assigned);
        assertEquals(lng, fleet.get(0).getHomeLng());
        assertEquals(lat, fleet.get(0).getHomeLat());
    }

    @Test
    void should_be_deterministic_for_same_seed() {
        List<Bike> first = fleet(50);
        List<Bike> second = fleet(50);
        List<FenceSite> fences = List.of(
                new FenceSite(1L, "[停放50] 甲", rect(LNG, LAT, 0.001, 0.001), 50),
                new FenceSite(2L, "[停放50] 乙", rect(LNG + 0.01, LAT, 0.001, 0.001), 50));

        new FenceParkingPlanner(42L).assign(first, fences, 30);
        new FenceParkingPlanner(42L).assign(second, fences, 30);

        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getHomeLng(), second.get(i).getHomeLng());
            assertEquals(first.get(i).getHomeLat(), second.get(i).getHomeLat());
        }
    }

    private static List<Bike> fleet(int size) {
        List<Bike> fleet = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            fleet.add(new Bike("BMXC" + String.format("%06d", i), District.CENTER_CITY, LNG, LAT));
        }
        return fleet;
    }

    /** 闭合矩形外环：左下 → 右下 → 右上 → 左上 → 左下 */
    private static double[][] rect(double x, double y, double w, double h) {
        return new double[][]{
                {x, y}, {x + w, y}, {x + w, y + h}, {x, y + h}, {x, y}
        };
    }

    private static boolean insideAny(List<FenceSite> fences, double lng, double lat) {
        for (FenceSite fence : fences) {
            if (RayCasting.contains(fence.getRing(), lng, lat)) {
                return true;
            }
        }
        return false;
    }

    private static int countIn(FenceSite fence, List<Bike> fleet) {
        int count = 0;
        for (Bike bike : fleet) {
            if (RayCasting.contains(fence.getRing(), bike.getHomeLng(), bike.getHomeLat())) {
                count++;
            }
        }
        return count;
    }
}
