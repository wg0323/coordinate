package com.bike.monitor.server.service;

import com.bike.monitor.server.dto.TracePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * TDengine 轨迹查询客户端（REST 6041，单连接串行查询——演示场景够用）。
 */
@Slf4j
@Component
public class TdengineClient {

    private final String url;

    public TdengineClient(@Value("${tdengine.url}") String url) {
        this.url = url;
    }

    /** 查询某车时间窗内轨迹（按 ts 升序），异常返回空列表 */
    public synchronized List<TracePoint> queryTrace(String bikeId, long fromMs, long toMs, int limit) {
        List<TracePoint> points = new ArrayList<>();
        String sql = "SELECT ts, lng, lat, speed FROM gps_db.gps_trace "
                + "WHERE tbname = ? AND ts >= ? AND ts <= ? ORDER BY ts ASC";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "t_bike_" + bikeId);
            ps.setString(2, new java.sql.Timestamp(fromMs).toString());
            ps.setString(3, new java.sql.Timestamp(toMs).toString());
            ps.setMaxRows(limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    points.add(new TracePoint(rs.getTimestamp(1).getTime(),
                            rs.getDouble(2), rs.getDouble(3), rs.getDouble(4)));
                }
            }
        } catch (Exception e) {
            log.warn("TDengine trace query failed: {}", e.getMessage());
        }
        return points;
    }
}
