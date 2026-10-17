package com.bike.monitor.flink.clean.sink;

import com.bike.monitor.common.dto.GpsPoint;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * TDengine 轨迹落库（题 5 / 知识点 4）：jdbc:TAOS-RS（REST 6041）。
 *
 * <p>写入线程与 Flink 数据流解耦：invoke 只投递有界队列（队列满则丢弃并计数——演示可容忍），
 * 专职写线程攒批写；任何 TDengine 卡顿都不会反压拖死整条作业链。
 * 建库带 KEEP 90d 保留策略（容量维度：自动滚动删除过期轨迹）。</p>
 */
public class TdengineSink extends RichSinkFunction<GpsPoint> {

    private static final Logger log = LoggerFactory.getLogger(TdengineSink.class);
    private static final int QUEUE_CAPACITY = 50_000;
    /** 单条 INSERT 的最大行数（一条 HTTP 请求写多子表） */
    private static final int SQL_ROW_CHUNK = 2000;

    private final String jdbcUrl;
    /** Flink 序列化后 transient 字段为 null，统一在 open() 初始化 */
    private transient BlockingQueue<GpsPoint> queue;
    private transient Connection connection;
    private transient Thread writerThread;
    private volatile boolean running = true;
    private long written = 0;
    private long dropped = 0;

    public TdengineSink(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        Class.forName("com.taosdata.jdbc.rs.RestfulDriver");
        // REST 连接超时：防止 taosadapter 偶发无响应时写入线程永久阻塞
        connection = DriverManager.getConnection(jdbcUrl
                + "?user=root&password=taosdata&httpConnectTimeout=5000&httpSocketTimeout=30000");
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS gps_db KEEP 90d");
            st.execute("CREATE STABLE IF NOT EXISTS gps_db.gps_trace ("
                    + "ts TIMESTAMP, lng DOUBLE, lat DOUBLE, speed DOUBLE, direction FLOAT, locked TINYINT) "
                    + "TAGS (district NCHAR(16), bike_type TINYINT)");
        }
        writerThread = new Thread(this::writeLoop, "tdengine-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    @Override
    public void invoke(GpsPoint point, Context context) {
        if (!queue.offer(point)) {
            dropped++;
            if (dropped % 10_000 == 1) {
                log.warn("TDengine queue full, dropped={} total", dropped);
            }
        }
    }

    /** 专职写线程：攒批（≤2000 行）后以超级表 tbname 语法批量写 */
    private void writeLoop() {
        List<GpsPoint> batch = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        while (running || !queue.isEmpty()) {
            try {
                GpsPoint first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, SQL_ROW_CHUNK - 1);
                // 一条 SQL 写多个子表（tbname 伪列内联 TAG 值，自动建表）
                StringBuilder sql = new StringBuilder(
                        "INSERT INTO gps_db.gps_trace (tbname, ts, lng, lat, speed, direction, locked, district, bike_type) VALUES ");
                for (int i = 0; i < batch.size(); i++) {
                    GpsPoint p = batch.get(i);
                    if (i > 0) {
                        sql.append(' ');
                    }
                    sql.append("('t_bike_").append(p.getBikeId())
                            .append("','").append(fmt.format(new Date(p.getTs())))
                            .append("',").append(p.getLng()).append(',').append(p.getLat())
                            .append(',').append(p.getSpeed()).append(',').append(p.getDirection())
                            .append(',').append(p.isLocked() ? 1 : 0)
                            .append(",'").append(p.getDistrict().replace("'", "''"))
                            .append("',0)");
                }
                try (Statement st = connection.createStatement()) {
                    st.execute(sql.toString());
                    written += batch.size();
                } catch (Exception e) {
                    log.warn("TDengine write failed, dropped {} points: {}", batch.size(), e.getMessage());
                }
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("TDengine writer exited, total written={} dropped={}", written, dropped);
    }

    @Override
    public void close() {
        running = false;
        if (writerThread != null) {
            try {
                writerThread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
    }
}
