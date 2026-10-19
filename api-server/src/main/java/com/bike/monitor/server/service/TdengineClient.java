package com.bike.monitor.server.service;

import com.bike.monitor.server.dto.TracePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

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
        // ts 用 CAST 取回 epoch 毫秒，避开 JDBC Timestamp 的 JVM 时区解释差异；
        // TDengine 中的时间字面量由 Flink（VM 默认 UTC）写入，查询条件同样按 UTC 格式化。
        String sql = "SELECT CAST(ts AS BIGINT) AS ts_ms, lng, lat, speed FROM gps_db.gps_trace "
                + "WHERE tbname = ? AND ts >= ? AND ts <= ? ORDER BY ts ASC";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "t_bike_" + bikeId);
            ps.setString(2, formatUtc(fromMs));
            ps.setString(3, formatUtc(toMs));
            ps.setMaxRows(limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    points.add(new TracePoint(rs.getLong(1),
                            rs.getDouble(2), rs.getDouble(3), rs.getDouble(4)));
                }
            }
        } catch (Exception e) {
            log.warn("TDengine trace query failed: {}", e.getMessage());
        }
        return points;
    }

    private static String formatUtc(long ms) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(ms));
    }
}
