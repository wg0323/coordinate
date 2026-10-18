package com.bike.monitor.server.service;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 车辆状态 hash 值编解码（Redis key bike:status，value = "locked,district,ts"）。
 * 与 flink-jobs RedisGeoSink 的写入格式互为协定。
 */
public final class BikeStatusCodec {

    private BikeStatusCodec() {
    }

    @Data
    @AllArgsConstructor
    public static class StatusEntry {
        private boolean locked;
        private String district;
        private long ts;
    }

    /** 解析 hash 值；格式非法返回 null（脏数据由调用方跳过） */
    public static StatusEntry decode(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            return null;
        }
        long ts;
        try {
            ts = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (!"0".equals(parts[0]) && !"1".equals(parts[0])) {
            return null;
        }
        return new StatusEntry("1".equals(parts[0]), parts[1], ts);
    }
}
