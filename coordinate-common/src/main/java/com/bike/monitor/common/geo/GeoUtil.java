package com.bike.monitor.common.geo;

/**
 * Haversine 球面距离与速度估算（面试手撕公式备好，知识点 3）。
 */
public final class GeoUtil {

    /** 平均地球半径（米） */
    private static final double EARTH_RADIUS = 6371000.0;

    private GeoUtil() {
    }

    /**
     * Haversine 公式计算球面两点距离（米）。
     */
    public static double haversine(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * 平均速度（m/s）= 距离 / 时间。
     */
    public static double speedMps(double distanceMeters, long seconds) {
        return distanceMeters / seconds;
    }
}
