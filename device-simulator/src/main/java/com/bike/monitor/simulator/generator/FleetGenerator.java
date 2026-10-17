package com.bike.monitor.simulator.generator;

import com.bike.monitor.simulator.model.Bike;
import com.bike.monitor.simulator.model.District;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 按区县建模表生成全量车队（84,840 辆）：bikeId、区县、常驻点。
 * 仅用于首次启动向 Mongo seed 车队台账。
 */
public class FleetGenerator {

    private final Random random;

    public FleetGenerator(long seed) {
        this.random = new Random(seed);
    }

    public List<Bike> generate() {
        List<Bike> fleet = new ArrayList<>();
        for (District district : District.all()) {
            for (int i = 0; i < district.getVehicles(); i++) {
                String bikeId = "BM" + district.getCode()
                        + String.format("%06d", i);
                double homeLng = district.getLng() + (random.nextDouble() * 2 - 1) * district.getLngSpan();
                double homeLat = district.getLat() + (random.nextDouble() * 2 - 1) * district.getLatSpan();
                fleet.add(new Bike(bikeId, district, homeLng, homeLat));
            }
        }
        return fleet;
    }
}
