package com.bike.monitor.simulator.model;

import lombok.Getter;

/**
 * 一次骑行：起终点直线路径插值 + 小幅正弦摆动模拟真实骑行轨迹。
 * 时间均为模拟时间（epoch millis，随 speedFactor 压缩）。
 */
@Getter
public class Ride {

    private final String bikeId;
    private final String district;
    private final double originLng;
    private final double originLat;
    private final double destLng;
    private final double destLat;
    private final long startSimMs;
    private final long durationSimMs;
    private final double speedMps;
    private final float bearingDeg;

    public Ride(String bikeId, String district,
                double originLng, double originLat,
                double destLng, double destLat,
                long startSimMs, long durationSimMs, double speedMps) {
        this.bikeId = bikeId;
        this.district = district;
        this.originLng = originLng;
        this.originLat = originLat;
        this.destLng = destLng;
        this.destLat = destLat;
        this.startSimMs = startSimMs;
        this.durationSimMs = durationSimMs;
        this.speedMps = speedMps;
        this.bearingDeg = bearing(originLng, originLat, destLng, destLat);
    }

    public long endSimMs() {
        return startSimMs + durationSimMs;
    }

    /** 路径插值：线性 + 正弦小幅摆动（模拟绕行） */
    public double[] positionAt(long simMs) {
        double frac = Math.min(1.0, Math.max(0.0, (simMs - startSimMs) / (double) durationSimMs));
        double lng = originLng + (destLng - originLng) * frac + 0.00015 * Math.sin(simMs / 7000.0);
        double lat = originLat + (destLat - originLat) * frac + 0.00012 * Math.cos(simMs / 9000.0);
        return new double[]{lng, lat};
    }

    private static float bearing(double lng1, double lat1, double lng2, double lat2) {
        double dx = (lng2 - lng1) * Math.cos(Math.toRadians(lat1));
        double dy = lat2 - lat1;
        double deg = Math.toDegrees(Math.atan2(dx, dy));
        return (float) ((deg + 360) % 360);
    }
}
