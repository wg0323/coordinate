package com.bike.monitor.simulator.history;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.simulator.config.SimConfig;
import com.bike.monitor.simulator.generator.DayTraceGenerator;
import com.bike.monitor.simulator.report.Reporter;
import com.bike.monitor.simulator.roadnet.RoadNet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 固定设备历史轨迹灌入：生成“昨天”的整日骑行轨迹并发 Kafka，发完退出。
 * 必须保证 Flink GpsCleanJob 已先启动（Kafka 源从 latest 位点消费）。
 */
public final class HistoryTraceRunner {

    private static final Logger log = LoggerFactory.getLogger(HistoryTraceRunner.class);

    private HistoryTraceRunner() {
    }

    public static void run(SimConfig config) throws Exception {
        RoadNet net = RoadNet.loadFromClasspath("roadnet/beijing-center.json");
        LocalDate day = LocalDate.now().minusDays(config.getTraceDayOffset());
        long dayStartMs = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<GpsPoint> points = new DayTraceGenerator(net, config.getTraceSeed())
                .generate(config.getTraceBike(), config.getTraceDistrict(), dayStartMs, config.getTraceSegments());

        double ridingM = 0;
        long ridingMs = 0;
        for (int i = 1; i < points.size(); i++) {
            GpsPoint a = points.get(i - 1);
            GpsPoint b = points.get(i);
            if (b.getSpeed() > 0) {
                ridingM += RoadNet.haversine(a.getLng(), a.getLat(), b.getLng(), b.getLat());
                ridingMs += b.getTs() - a.getTs();
            }
        }
        log.info("生成完成：设备={}，日期={}，段数={}，点数={}，里程≈{}km，骑行时长≈{}min，时间窗口=[{}, {}]",
                config.getTraceBike(), day, config.getTraceSegments(), points.size(),
                Math.round(ridingM / 100) / 10.0, ridingMs / 60000,
                points.get(0).getTs(), points.get(points.size() - 1).getTs());

        try (Reporter reporter = new Reporter(config.getKafkaServers(), config.getTopic())) {
            int sent = 0;
            for (GpsPoint point : points) {
                reporter.send(point);
                if (++sent % 1000 == 0) {
                    Thread.sleep(100);
                }
            }
        }
        log.info("历史轨迹已发送至 {} topic={}，共 {} 条", config.getKafkaServers(), config.getTopic(), points.size());
    }
}
