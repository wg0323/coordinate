package com.bike.monitor.flink.util;

import com.bike.monitor.common.dto.FenceRule;
import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.common.dto.RideEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * GpsPoint JSON 编解码（Kafka 载荷），ObjectMapper 线程安全可静态复用。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtil() {
    }

    public static String toJson(GpsPoint point) {
        try {
            return MAPPER.writeValueAsString(point);
        } catch (Exception e) {
            throw new IllegalStateException("GpsPoint 序列化失败", e);
        }
    }

    /** 解析失败返回 null（脏消息直接丢弃，不中断作业） */
    public static GpsPoint parseGpsPoint(String json) {
        try {
            return MAPPER.readValue(json, GpsPoint.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static String toJson(RideEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("RideEvent 序列化失败", e);
        }
    }

    /** fence-config 消息解析（兼容上游 BOM/首尾空白），失败返回 null */
    public static FenceRule parseFenceRule(String json) {
        try {
            String cleaned = json.replace("\uFEFF", "").trim();
            return MAPPER.readValue(cleaned, FenceRule.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
