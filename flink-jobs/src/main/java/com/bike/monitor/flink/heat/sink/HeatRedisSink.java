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

    // Redis key 前缀：heat:{窗口起始毫秒}，每个窗口一个 hash。
    private static final String KEY_PREFIX = "heat:";
    // key 保留 3 分钟（约 2 个窗口兜底），防止历史窗口 key 无限膨胀。
    private static final int TTL_SECONDS = 180;

    // Redis 地址（host:port）。
    private final String hostPort;
    // Jedis 连接池：open 时创建、close 时释放（transient 不参与 Flink 序列化）。
    private transient JedisPool pool;

    public HeatRedisSink(String hostPort) {
        this.hostPort = hostPort;
    }

    // 每个 subtask 独立建池，连接超时 2s。
    @Override
    public void open(Configuration parameters) {
        String[] parts = hostPort.split(":");
        pool = new JedisPool(new JedisPoolConfig(), parts[0], Integer.parseInt(parts[1]), 2000);
    }

    // 按窗口 key 写入 H3 单元格计数并刷新 TTL（同 key 重复写幂等）。
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

    // 作业关闭时释放连接池。
    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
