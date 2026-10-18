package com.bike.monitor.server.service;

import com.bike.monitor.server.dto.StatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 全局车辆统计：Redis bike:status（在线/骑行/空闲/分区）+ Mongo bikes 台账总数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    public static final String STATUS_KEY = "bike:status";
    private static final String FLEET_COLLECTION = "bikes";

    private final StringRedisTemplate redis;
    private final MongoTemplate mongo;

    public StatsDto stats() {
        long total = mongo.count(new Query(), FLEET_COLLECTION);
        Map<String, String> statusHash = redis.<String, String>opsForHash().entries(STATUS_KEY);
        return StatsCalculator.compute(total, statusHash, System.currentTimeMillis());
    }
}
