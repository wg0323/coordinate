package com.bike.monitor.flink.fence;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.common.dto.RideEvent;
import com.bike.monitor.flink.clean.sink.CleanKafkaSink;
import com.bike.monitor.flink.fence.sink.AlertKafkaSink;
import com.bike.monitor.flink.util.JsonUtil;
import com.bike.monitor.flink.util.JobSetup;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Duration;
import java.util.Objects;

/**
 * 作业 2：围栏进出判定（题 8/9/10）。
 * bike-gps-clean（打 watermark）+ fence-config（广播流，原始 JSON 字符串直通，
 * 由算子内部解析——规避 POJO/Kryo 在广播边的序列化问题）→ KeyedBroadcastProcessFunction
 * → STRtree 粗筛 + JTS covers 精筛 + 事件时间定时器防抖 → bike-fence-alert。
 */
public class FenceJob {

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);
        StreamExecutionEnvironment env = JobSetup.setup(params);
        buildGraph(env, params);
        env.execute("FenceJob");
    }

    /** 作业图构建（独立方法便于本地调试/测试复用） */
    public static void buildGraph(StreamExecutionEnvironment env, ParameterTool params) {
        String kafkaServers = params.get("kafka", "kafka:9092");

        // clean 流：以 point.ts 作为事件时间打 watermark（防抖事件时间定时器依赖），容忍 5s 乱序
        KafkaSource<String> gpsSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaServers)
                .setTopics(CleanKafkaSink.TOPIC)
                .setGroupId("fence-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                // 动态发现新分区（默认关闭；topic 扩分区后不重启作业也能感知）
                .setProperty("partition.discovery.interval.ms", "10000")
                .build();
        DataStream<GpsPoint> gps = env.fromSource(gpsSource,
                        WatermarkStrategy.noWatermarks(), "kafka-gps-clean")
                .map(JsonUtil::parseGpsPoint, TypeInformation.of(GpsPoint.class)).name("parse-json")
                .filter(Objects::nonNull).name("drop-invalid-json")
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<GpsPoint>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((point, ts) -> point.getTs())
                                // 分区静默时标记 idle，不参与下游最小水印计算
                                .withIdleness(Duration.ofSeconds(60)));

        // 围栏配置流：原始 JSON 直通广播（api-server CRUD 双写 / 测试用 console producer）。
        // 关键：双输入算子水印取所有通道最小值——config 流必须发「不拖后腿」的水印
        // （记录即发 MAX 水印 + 空闲排除），否则广播通道 MIN_VALUE 会钉死 judge 的定时器。
        KafkaSource<String> configSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaServers)
                .setTopics("fence-config")
                .setGroupId("fence-config-src")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
        DataStream<String> ruleJson = env.fromSource(configSource,
                WatermarkStrategy.<String>forMonotonousTimestamps()
                        .withTimestampAssigner((s, ts) -> Long.MAX_VALUE)
                        .withIdleness(Duration.ofSeconds(10)),
                "kafka-fence-config");

        DataStream<RideEvent> alerts = gps.keyBy(GpsPoint::getBikeId)
                .connect(ruleJson.broadcast(FenceJudgeProcess.RULE_DESCRIPTOR))
                .process(new FenceJudgeProcess()).name("fence-judge");

        alerts.map(JsonUtil::toJson, org.apache.flink.api.common.typeinfo.Types.STRING)
                .name("event-to-json")
                .sinkTo(AlertKafkaSink.build(kafkaServers)).name("bike-fence-alert-sink");
    }
}
