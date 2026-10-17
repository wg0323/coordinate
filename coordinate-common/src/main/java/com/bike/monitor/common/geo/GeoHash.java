package com.bike.monitor.common.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * GeoHash 编解码（二分 + Base32）与九宫格邻接查询。
 *
 * <p>参数约定：lng 在前、lat 在后。精度 precision 为 Base32 字符位数，
 * 每字符 5 bit，奇数位以经度开始交替二分。</p>
 *
 * <p>邻接查询用于解决 GeoHash 边界突变缺陷：查询时连同周围 8 个网格一起查，
 * 再用 Haversine 做二次过滤（题 6）。</p>
 */
public final class GeoHash {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    private GeoHash() {
    }

    /** 编码：返回 precision 位 Base32 字符串 */
    public static String encode(double lng, double lat, int precision) {
        double latMin = -90, latMax = 90;
        double lngMin = -180, lngMax = 180;
        StringBuilder sb = new StringBuilder(precision);
        int bit = 0;
        int ch = 0;
        boolean even = true;
        while (sb.length() < precision) {
            if (even) {
                double mid = (lngMin + lngMax) / 2;
                if (lng >= mid) {
                    ch = (ch << 1) | 1;
                    lngMin = mid;
                } else {
                    ch <<= 1;
                    lngMax = mid;
                }
            } else {
                double mid = (latMin + latMax) / 2;
                if (lat >= mid) {
                    ch = (ch << 1) | 1;
                    latMin = mid;
                } else {
                    ch <<= 1;
                    latMax = mid;
                }
            }
            even = !even;
            if (++bit % 5 == 0) {
                sb.append(BASE32.charAt(ch));
                ch = 0;
            }
        }
        return sb.toString();
    }

    /**
     * 解码为包围盒 {minLng, maxLng, minLat, maxLat}。
     */
    public static double[] decodeBbox(String geohash) {
        double latMin = -90, latMax = 90;
        double lngMin = -180, lngMax = 180;
        boolean even = true;
        for (int i = 0; i < geohash.length(); i++) {
            int idx = BASE32.indexOf(geohash.charAt(i));
            if (idx < 0) {
                throw new IllegalArgumentException("非法 GeoHash 字符: " + geohash.charAt(i));
            }
            for (int bit = 4; bit >= 0; bit--) {
                int mask = 1 << bit;
                if (even) {
                    if ((idx & mask) != 0) {
                        lngMin = (lngMin + lngMax) / 2;
                    } else {
                        lngMax = (lngMin + lngMax) / 2;
                    }
                } else {
                    if ((idx & mask) != 0) {
                        latMin = (latMin + latMax) / 2;
                    } else {
                        latMax = (latMin + latMax) / 2;
                    }
                }
                even = !even;
            }
        }
        return new double[]{lngMin, lngMax, latMin, latMax};
    }

    /** 解码为包围盒中心点 {lng, lat} */
    public static double[] decode(String geohash) {
        double[] b = decodeBbox(geohash);
        return new double[]{(b[0] + b[1]) / 2, (b[2] + b[3]) / 2};
    }

    /**
     * 九宫格查询：自身 + 周围 8 个相邻网格的 geohash。
     *
     * <p>实现方式：按自身包围盒尺寸向四周平移取中心点重新编码，
     * 在极点/本初子午线附近（本系统仅限中国区域）不涉及环绕处理。</p>
     */
    public static List<String> around(String geohash) {
        double[] b = decodeBbox(geohash);
        double cellLng = b[1] - b[0];
        double cellLat = b[3] - b[2];
        int precision = geohash.length();
        List<String> result = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                double lng = b[0] + cellLng * (dx + 0.5);
                double lat = b[2] + cellLat * (dy + 0.5);
                result.add(encode(lng, lat, precision));
            }
        }
        return result;
    }
}
