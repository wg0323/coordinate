package com.bike.monitor.flink.clean.sink;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.flink.util.JsonUtil;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.api.common.serialization.SimpleStringSchema;

/**
 * 清洗后点位回写 Kafka（bike-gps-clean，供围栏/热力作业与对比演示消费）。
 */
public final class CleanKafkaSink {

    public static final String TOPIC = "bike-gps-clean";

    private CleanKafkaSink() {
    }

    public static KafkaSink<String> build(String bootstrapServers) {
        return KafkaSink.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(TOPIC)
                        .setKeySerializationSchema(new SimpleStringSchema())
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();
    }

    /** GpsPoint → JSON（key = bikeId 保证同车分区有序） */
    public static String toJson(GpsPoint point) {
        return JsonUtil.toJson(point);
    }
}
