package com.bike.monitor.simulator.noise;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.simulator.generator.RideGenerator;

import java.util.Random;

/**
 * GPS 脏数据注入器（题 1 的数据源）：按概率注入三类漂移。
 * 注入发生在消息副本上，不影响引擎内部位置状态。
 */
public class DriftInjector {

    public enum Type { TELEPORT, JITTER, OUTLIER }

    private final double rate;
    private final Random random;
    private boolean jitterFlip = false;

    public DriftInjector(double rate, long seed) {
        this.rate = rate;
        this.random = new Random(seed);
    }

    /** 返回注入后的消息副本；未命中概率时返回原消息 */
    public GpsPoint inject(GpsPoint point) {
        if (random.nextDouble() >= rate) {
            return point;
        }
        Type type = Type.values()[random.nextInt(Type.values().length)];
        double distM;
        switch (type) {
            case TELEPORT:
                distM = 500 + random.nextDouble() * 1500;
                break;
            case JITTER:
                jitterFlip = !jitterFlip;
                distM = (jitterFlip ? 1 : -1) * (50 + random.nextDouble() * 50);
                break;
            case OUTLIER:
                distM = 3000 + random.nextDouble() * 7000;
                break;
            default:
                return point;
        }
        double bearing = random.nextDouble() * 360;
        double[] drifted = RideGenerator.offset(point.getLng(), point.getLat(), Math.abs(distM), bearing);
        return GpsPoint.builder()
                .bikeId(point.getBikeId())
                .district(point.getDistrict())
                .lng(drifted[0])
                .lat(drifted[1])
                .speed(point.getSpeed())
                .direction(point.getDirection())
                .locked(point.isLocked())
                .ts(point.getTs())
                .build();
    }
}
