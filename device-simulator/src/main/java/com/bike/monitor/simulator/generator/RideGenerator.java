package com.bike.monitor.simulator.generator;

import com.bike.monitor.simulator.model.Bike;
import com.bike.monitor.simulator.model.Ride;

import java.util.Random;

/**
 * 按周转率泊松生成骑行：随机起终点、随机车速，路径时长 = 距离 / 车速。
 */
public class RideGenerator {

    private static final double METERS_PER_DEG_LAT = 111320.0;
    /** 平均单次骑行时长（秒），design.md 5.1 */
    private static final long AVG_RIDE_SEC = 720;

    private final Random random;

    public RideGenerator(long seed) {
        this.random = new Random(seed);
    }

    /** 下一次骑行开始的间隔（模拟毫秒）：泊松到达，均值 = 86400s / 周转率 */
    public long nextRideStartGapMs(Bike bike) {
        double meanSec = 86400.0 / bike.getDistrict().getTurnover();
        return (long) (-Math.log(1 - random.nextDouble()) * meanSec * 1000);
    }

    public Ride randomRide(Bike bike, long startSimMs) {
        double speed = 2.5 + random.nextDouble() * 2.0;
        long duration = (long) (AVG_RIDE_SEC * (0.7 + random.nextDouble() * 0.6)) * 1000;
        double distanceM = speed * duration / 1000.0;
        double bearing = random.nextDouble() * 360;
        // 起点在常驻点附近 300m 内
        double[] origin = offset(bike.getHomeLng(), bike.getHomeLat(),
                random.nextDouble() * 300, random.nextDouble() * 360);
        double[] dest = offset(origin[0], origin[1], distanceM, bearing);
        return new Ride(bike.getBikeId(), bike.getDistrict().getName(),
                origin[0], origin[1], dest[0], dest[1],
                startSimMs, duration, speed);
    }

    /** 伴随车辆：与领骑车同起终点（题 3 套牌车演示） */
    public Ride companionRide(Ride leader, String followerId, long startSimMs) {
        return new Ride(followerId, leader.getDistrict(),
                leader.getOriginLng(), leader.getOriginLat(),
                leader.getDestLng(), leader.getDestLat(),
                startSimMs, leader.getDurationSimMs(), leader.getSpeedMps());
    }

    public static double[] offset(double lng, double lat, double distM, double bearingDeg) {
        double rad = Math.toRadians(bearingDeg);
        double dLat = distM * Math.cos(rad) / METERS_PER_DEG_LAT;
        double dLng = distM * Math.sin(rad) / (METERS_PER_DEG_LAT * Math.cos(Math.toRadians(lat)));
        return new double[]{lng + dLng, lat + dLat};
    }
}
