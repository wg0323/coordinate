package com.bike.monitor.common.geo;

/**
 * WGS84 / GCJ02 / BD09 三系坐标转换（国测局标准偏移公式的通用 Java 实现）。
 *
 * <p>精度口径：GCJ02→WGS84 为迭代近似逆变换（3 次迭代），偏差 < 1e-5 度（约 1 米级），
 * GCJ 偏移本身就是非线性加密，不存在解析精确逆。</p>
 */
public final class CoordConverter {

    private static final double PI = Math.PI;
    private static final double X_PI = PI * 3000.0 / 180.0;
    /** 克拉索夫斯基椭球长半轴 */
    private static final double A = 6378245.0;
    /** 第一偏心率平方 */
    private static final double EE = 0.00669342162296594623;

    private CoordConverter() {
    }

    /** WGS84 → GCJ02；境外坐标原样返回 */
    public static double[] wgs84ToGcj02(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }
        double[] delta = delta(lng, lat);
        return new double[]{lng + delta[0], lat + delta[1]};
    }

    /** GCJ02 → WGS84，迭代近似逆变换 */
    public static double[] gcj02ToWgs84(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }
        double wgsLng = lng;
        double wgsLat = lat;
        for (int i = 0; i < 3; i++) {
            double[] gcjOfWgs = wgs84ToGcj02(wgsLng, wgsLat);
            wgsLng -= gcjOfWgs[0] - lng;
            wgsLat -= gcjOfWgs[1] - lat;
        }
        return new double[]{wgsLng, wgsLat};
    }

    /** GCJ02 → BD09（百度在 GCJ02 基础上二次加密） */
    public static double[] gcj02ToBd09(double lng, double lat) {
        double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI);
        double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI);
        return new double[]{z * Math.cos(theta) + 0.0065, z * Math.sin(theta) + 0.006};
    }

    /** BD09 → GCJ02 */
    public static double[] bd09ToGcj02(double lng, double lat) {
        double x = lng - 0.0065;
        double y = lat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
        return new double[]{z * Math.cos(theta), z * Math.sin(theta)};
    }

    /** WGS84 → BD09 */
    public static double[] wgs84ToBd09(double lng, double lat) {
        double[] gcj = wgs84ToGcj02(lng, lat);
        return gcj02ToBd09(gcj[0], gcj[1]);
    }

    /** BD09 → WGS84 */
    public static double[] bd09ToWgs84(double lng, double lat) {
        double[] gcj = bd09ToGcj02(lng, lat);
        return gcj02ToWgs84(gcj[0], gcj[1]);
    }

    private static boolean outOfChina(double lng, double lat) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double[] delta(double lng, double lat) {
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{dLng, dLat};
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
}
