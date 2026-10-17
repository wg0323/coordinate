package com.bike.monitor.flink.heat.sink;

import com.bike.monitor.flink.heat.HeatCell;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * 热力结果写 Redis：HSET heat:{windowStart} h3Index=count，TTL 3 分钟（2 个窗口兜底防 key 膨胀）。
 */
public class HeatRedisSink extends RichSinkFunction<HeatCell> {

    private static final String KEY_PREFIX = "heat:";
    private static final int TTL_SECONDS = 180;

    private final String hostPort;
    private transient JedisPool pool;

    public HeatRedisSink(String hostPort) {
        this.hostPort = hostPort;
    }

    @Override
    public void open(Configuration parameters) {
        String[] parts = hostPort.split(":");
        pool = new JedisPool(new JedisPoolConfig(), parts[0], Integer.parseInt(parts[1]), 2000);
    }

    @Override
    public void invoke(HeatCell cell, Context context) {
        try (Jedis jedis = pool.getResource()) {
            String key = KEY_PREFIX + cell.getWindowStart();
            jedis.hset(key, String.valueOf(cell.getH3Index()), String.valueOf(cell.getCount()));
            jedis.expire(key, TTL_SECONDS);
        } catch (Exception e) {
            // Redis 抖动丢弃该窗口分片，下个窗口自愈
        }
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
