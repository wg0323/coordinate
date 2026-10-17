package com.bike.monitor.simulator.generator;

import com.bike.monitor.simulator.model.Bike;
import com.bike.monitor.simulator.model.District;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 按区县建模表生成车队：bikeId、区县、常驻点。
 */
public class FleetGenerator {

    private final double vehicleRatio;
    private final Random random;

    public FleetGenerator(double vehicleRatio, long seed) {
        this.vehicleRatio = vehicleRatio;
        this.random = new Random(seed);
    }

    public List<Bike> generate() {
        List<Bike> fleet = new ArrayList<>();
        for (District district : District.all()) {
            int count = (int) Math.round(district.getVehicles() * vehicleRatio);
            for (int i = 0; i < count; i++) {
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
