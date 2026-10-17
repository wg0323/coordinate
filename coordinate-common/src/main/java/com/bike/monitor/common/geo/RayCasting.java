package com.bike.monitor.common.geo;

/**
 * 自实现射线法（Ray Casting）点在多边形判定，含包围盒预判与边界处理（题 8）。
 *
 * <p>边界语义与 JTS {@code Polygon.covers(Point)} 一致：边界与顶点视为在多边形内
 * （注意 JTS {@code contains} 不含边界，围栏判定按「触及即在内」处理）。</p>
 */
public final class RayCasting {

    /** 共线判定误差（坐标单位为度，量级 1e-9 已足够覆盖双精度噪声） */
    private static final double EPS = 1e-9;

    private RayCasting() {
    }

    /**
     * 判定点 (lng, lat) 是否在多边形内。
     *
     * @param polygon 外环顶点数组，polygon[i] = {lng, lat}，不要求首尾闭合
     */
    public static boolean contains(double[][] polygon, double lng, double lat) {
        if (polygon == null || polygon.length < 3) {
            return false;
        }
        if (!inBbox(polygon, lng, lat)) {
            return false;
        }
        // 边界/顶点显式判为内部，与 JTS 语义对齐
        if (onBoundary(polygon, lng, lat)) {
            return true;
        }
        // 水平向右射线，统计交点数：奇数在内、偶数在外
        boolean inside = false;
        int n = polygon.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon[i][0], yi = polygon[i][1];
            double xj = polygon[j][0], yj = polygon[j][1];
            if ((yi > lat) != (yj > lat)
                    && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static boolean inBbox(double[][] polygon, double lng, double lat) {
        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        for (double[] p : polygon) {
            minLng = Math.min(minLng, p[0]);
            maxLng = Math.max(maxLng, p[0]);
            minLat = Math.min(minLat, p[1]);
            maxLat = Math.max(maxLat, p[1]);
        }
        return lng >= minLng && lng <= maxLng && lat >= minLat && lat <= maxLat;
    }

    private static boolean onBoundary(double[][] polygon, double lng, double lat) {
        int n = polygon.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if (onSegment(polygon[j][0], polygon[j][1], polygon[i][0], polygon[i][1], lng, lat)) {
                return true;
            }
        }
        return false;
    }

    /** 点 p 是否在线段 (x1,y1)-(x2,y2) 上 */
    private static boolean onSegment(double x1, double y1, double x2, double y2,
                                     double px, double py) {
        double cross = (x2 - x1) * (py - y1) - (y2 - y1) * (px - x1);
        if (Math.abs(cross) > EPS) {
            return false;
        }
        return px >= Math.min(x1, x2) - EPS && px <= Math.max(x1, x2) + EPS
                && py >= Math.min(y1, y2) - EPS && py <= Math.max(y1, y2) + EPS;
    }
}
