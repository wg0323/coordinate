package com.bike.monitor.flink.heat;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.flink.clean.sink.CleanKafkaSink;
import com.bike.monitor.flink.heat.sink.HeatRedisSink;
import com.bike.monitor.flink.util.JobSetup;
import com.bike.monitor.flink.util.JsonUtil;
import com.uber.h3core.H3Core;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;

/**
 * 作业 3：H3 热力聚合（题 6/11）。
 * bike-gps-clean → H3 res8 索引 → keyBy(cell) → 1 分钟事件时间滚动窗口聚合 distinct 车辆数
 * → Redis HSET heat:{windowStart}（题 11 的热力图数据出口）。
 */
public class H3AggJob {

    /** H3 分辨率：res8 平均六边形面积约 0.74 km²，适配城区热力图粒度 */
    public static final int H3_RES = 8;

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);
        StreamExecutionEnvironment env = JobSetup.setup(params);
        buildGraph(env, params);
        env.execute("H3AggJob");
    }

    public static void buildGraph(StreamExecutionEnvironment env, ParameterTool params) {
        String kafkaServers = params.get("kafka", "kafka:9092");
        String redis = params.get("redis", "redis:6379");

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaServers)
                .setTopics(CleanKafkaSink.TOPIC)
                .setGroupId("h3-agg-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperty("partition.discovery.interval.ms", "10000")
                .build();
        DataStream<GpsPoint> gps = env.fromSource(source,
                        WatermarkStrategy.noWatermarks(), "kafka-gps-clean")
                .map(JsonUtil::parseGpsPoint, org.apache.flink.api.common.typeinfo.TypeInformation.of(GpsPoint.class))
                .name("parse-json")
                .filter(Objects::nonNull).name("drop-invalid-json")
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<GpsPoint>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((point, ts) -> point.getTs())
                                .withIdleness(Duration.ofSeconds(60)));

        gps.keyBy(point -> toCell(point))
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .aggregate(
                        // 窗口内 distinct 车辆数：累加器存 bikeId 集合
                        new AggregateFunction<GpsPoint, HashSet<String>, Integer>() {
                            @Override
                            public HashSet<String> createAccumulator() {
                                return new HashSet<>();
                            }

                            @Override
                            public HashSet<String> add(GpsPoint value, HashSet<String> acc) {
                                acc.add(value.getBikeId());
                                return acc;
                            }

                            @Override
                            public Integer getResult(HashSet<String> acc) {
                                return acc.size();
                            }

                            @Override
                            public HashSet<String> merge(HashSet<String> a, HashSet<String> b) {
                                a.addAll(b);
                                return a;
                            }
                        },
                        new ProcessWindowFunction<Integer, HeatCell, Long, TimeWindow>() {
                            @Override
                            public void process(Long h3Index, Context ctx,
                                                Iterable<Integer> counts, Collector<HeatCell> out) {
                                int count = 0;
                                for (Integer c : counts) {
                                    count = c;
                                }
                                out.collect(new HeatCell(h3Index, ctx.window().getStart(), count));
                            }
                        })
                .name("h3-1min-agg")
                .addSink(new HeatRedisSink(redis)).name("heat-redis-sink");
    }

    private static Long toCell(GpsPoint point) {
        try {
            return H3CoreHolder.INSTANCE.latLngToCell(point.getLat(), point.getLng(), H3_RES);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** H3Core 实例化加载本地动态库，进程内复用单例 */
    private static class H3CoreHolder {
        static final H3Core INSTANCE = create();

        private static H3Core create() {
            try {
                return H3Core.newInstance();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("H3Core 初始化失败", e);
            }
        }
    }
}
