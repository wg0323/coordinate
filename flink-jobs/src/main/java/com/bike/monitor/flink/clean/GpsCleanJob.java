package com.bike.monitor.flink.clean;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.flink.clean.filter.KalmanLatLonFilter;
import com.bike.monitor.flink.clean.filter.MedianFilter;
import com.bike.monitor.flink.clean.filter.SpeedJumpFilter;
import com.bike.monitor.flink.clean.sink.CleanKafkaSink;
import com.bike.monitor.flink.clean.sink.RedisGeoSink;
import com.bike.monitor.flink.clean.sink.TdengineSink;
import com.bike.monitor.flink.util.JsonUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.Objects;

/**
 * 作业 1：GPS 清洗（题 1）。
 * 链路：bike-gps-raw → 速度/加速度物理极限过滤 → 卡尔曼滤波 → 5 点中值滤波
 * → 三个出口（单作业多 Sink）：bike-gps-clean topic + Redis GEO 最新位置 + TDengine 冷轨迹。
 */
public class GpsCleanJob {

    public static void main(String[] args) throws Exception {
        // 启动参数（--kafka/--redis/--tdengine/--checkpoint-dir/--parallelism/--backend），未传则用默认值。
        ParameterTool params = ParameterTool.fromArgs(args);
        String kafkaServers = params.get("kafka", "kafka:9092");
        String redis = params.get("redis", "redis:6379");
        String tdengineUrl = params.get("tdengine", "jdbc:TAOS-RS://tdengine:6041");
        String checkpointDir = params.get("checkpoint-dir", "file:///opt/flink/checkpoints");

        // 流执行环境：默认并行度 3。
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(params.getInt("parallelism", 3));

        // checkpoint：2 分钟一次，目录挂载宿主机 volume（容错恢复演示前提）。
        // 状态后端默认 heap（本系统状态量小，heap 足够；生产大状态口径切 RocksDB）；
        // --backend rocksdb 切换 RocksDB（生产大状态口径），checkpoint/恢复能力两者一致。
        env.enableCheckpointing(120_000, CheckpointingMode.EXACTLY_ONCE);
        if ("rocksdb".equals(params.get("backend", "heap"))) {
            env.setStateBackend(new EmbeddedRocksDBStateBackend());
        } else {
            env.setStateBackend(new HashMapStateBackend());
        }
        env.getCheckpointConfig().setCheckpointStorage(checkpointDir);
        env.getCheckpointConfig().setExternalizedCheckpointCleanup(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);

        // 输入源：bike-gps-raw 原始点位，从最新位点开始消费。
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaServers)
                .setTopics("bike-gps-raw")
                .setGroupId("gps-clean-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // JSON 解析为 GpsPoint，解析失败（null）的点直接丢弃。
        DataStream<GpsPoint> raw = env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-source")
                .map(JsonUtil::parseGpsPoint).name("parse-json")
                .filter(Objects::nonNull).name("drop-invalid-json");

        // 清洗链（按 bikeId 分区保序）：速度跳变过滤 → 卡尔曼滤波 → 5 点中值滤波。
        DataStream<GpsPoint> cleaned = raw
                .keyBy(GpsPoint::getBikeId)
                .process(new SpeedJumpFilter()).name("speed-jump-filter")
                .keyBy(GpsPoint::getBikeId)
                .process(new KalmanLatLonFilter()).name("kalman-filter")
                .keyBy(GpsPoint::getBikeId)
                .process(new MedianFilter()).name("median-filter");

        // 三路输出：清洗结果 topic、TDengine 冷轨迹、Redis GEO 最新位置。
        cleaned.map(CleanKafkaSink::toJson).name("to-json")
                .sinkTo(CleanKafkaSink.build(kafkaServers)).name("bike-gps-clean-sink");
        cleaned.addSink(new TdengineSink(tdengineUrl)).name("tdengine-sink");
        cleaned.addSink(new RedisGeoSink(redis)).name("redis-geo-sink");

        env.execute("GpsCleanJob");
    }
}
