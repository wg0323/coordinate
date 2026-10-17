package com.bike.monitor.simulator.report;

import com.bike.monitor.common.dto.GpsPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.LongAdder;

/**
 * Kafka 上报器：GpsPoint → JSON → bike-gps-raw（批量+ linger 攒批）。
 */
public class Reporter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Reporter.class);

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LongAdder sent = new LongAdder();
    private final LongAdder failed = new LongAdder();

    public Reporter(String bootstrapServers, String topic) {
        this.topic = topic;
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "20");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "131072");
        this.producer = new KafkaProducer<>(props);
    }

    public void send(GpsPoint point) {
        try {
            String json = objectMapper.writeValueAsString(point);
            producer.send(new ProducerRecord<>(topic, point.getBikeId(), json), (meta, ex) -> {
                if (ex != null) {
                    failed.increment();
                    log.warn("send failed: {}", ex.getMessage());
                } else {
                    sent.increment();
                }
            });
        } catch (Exception e) {
            failed.increment();
            log.warn("serialize failed", e);
        }
    }

    public long getSent() {
        return sent.sum();
    }

    public long getFailed() {
        return failed.sum();
    }

    @Override
    public void close() {
        producer.close();
    }
}
