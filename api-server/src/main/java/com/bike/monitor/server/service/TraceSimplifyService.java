package com.bike.monitor.server.service;

import com.bike.monitor.server.dto.TracePoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹抽稀（题 2）：自实现 Douglas-Peucker（递归垂距阈值）+ JTS DouglasPeuckerSimplifier 对照。
 */
@Service
public class TraceSimplifyService {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    /** 自实现 DP：对 (lng, lat) 平面做垂距递归，保留首尾点 */
    public List<TracePoint> douglasPeucker(List<TracePoint> points, double toleranceDeg) {
        if (points.size() <= 2) {
            return new ArrayList<>(points);
        }
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        dp(points, 0, points.size() - 1, toleranceDeg, keep);

        List<TracePoint> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    /** JTS DouglasPeuckerSimplifier 对照实现 */
    public List<TracePoint> jtsDouglasPeucker(List<TracePoint> points, double toleranceDeg) {
        if (points.size() <= 2) {
            return new ArrayList<>(points);
        }
        // 轨迹点转 JTS 坐标序列（仅经纬度参与抽稀，ts/speed 后续回填）。
        Coordinate[] coords = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            coords[i] = new Coordinate(points.get(i).getLng(), points.get(i).getLat());
        }
        LineString line = FACTORY.createLineString(coords);
        // JTS 内置 DP 抽稀，容差单位为度，与自实现版本同参对照。
        LineString simplified = (LineString) DouglasPeuckerSimplifier.simplify(line, toleranceDeg);
        List<TracePoint> result = new ArrayList<>();
        // 按坐标顺序映射回原始点（保留 ts/speed）
        int idx = 0;
        for (Coordinate c : simplified.getCoordinates()) {
            while (idx < points.size()
                    && (points.get(idx).getLng() != c.x || points.get(idx).getLat() != c.y)) {
                idx++;
            }
            if (idx < points.size()) {
                result.add(points.get(idx));
                idx++;
            }
        }
        return result;
    }

    private void dp(List<TracePoint> points, int start, int end, double tolerance, boolean[] keep) {
        if (end <= start + 1) {
            return;
        }
        TracePoint a = points.get(start);
        TracePoint b = points.get(end);
        double maxDist = -1;
        int maxIdx = -1;
        for (int i = start + 1; i < end; i++) {
            double d = perpendicularDistance(a, b, points.get(i));
            if (d > maxDist) {
                maxDist = d;
                maxIdx = i;
            }
        }
        if (maxDist >= tolerance) {
            keep[maxIdx] = true;
            dp(points, start, maxIdx, tolerance, keep);
            dp(points, maxIdx, end, tolerance, keep);
        }
    }

    /** 点到线段（首尾点连线）的垂距（近似平面度） */
    private double perpendicularDistance(TracePoint a, TracePoint b, TracePoint p) {
        double dx = b.getLng() - a.getLng();
        double dy = b.getLat() - a.getLat();
        double norm = Math.sqrt(dx * dx + dy * dy);
        if (norm == 0) {
            return Math.hypot(p.getLng() - a.getLng(), p.getLat() - a.getLat());
        }
        return Math.abs(dy * (p.getLng() - a.getLng()) - dx * (p.getLat() - a.getLat())) / norm;
    }
}
