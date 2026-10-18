package com.bike.monitor.flink.clean.sink;

import com.bike.monitor.common.dto.GpsPoint;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Redis 双写：GEOADD bike:latest lng lat bikeId（最新位置热数据），
 * HSET bike:status bikeId "locked,district,ts"（大屏状态统计源，与 api-server BikeStatusCodec 互为协定）。
 * 同车数据经 keyBy 后同分区有序，后写覆盖先写，天然只保留最新状态。
 */
public class RedisGeoSink extends RichSinkFunction<GpsPoint> {

    public static final String KEY = "bike:latest";
    public static final String STATUS_KEY = "bike:status";

    private final String hostPort;
    private transient JedisPool pool;

    public RedisGeoSink(String hostPort) {
        this.hostPort = hostPort;
    }

    @Override
    public void open(Configuration parameters) {
        String[] parts = hostPort.split(":");
        pool = new JedisPool(new JedisPoolConfig(), parts[0], Integer.parseInt(parts[1]), 2000);
    }

    @Override
    public void invoke(GpsPoint point, Context context) {
        try (Jedis jedis = pool.getResource()) {
            jedis.geoadd(KEY, point.getLng(), point.getLat(), point.getBikeId());
            String status = (point.isLocked() ? "1" : "0") + "," + point.getDistrict() + "," + point.getTs();
            jedis.hset(STATUS_KEY, point.getBikeId(), status);
        } catch (Exception e) {
            // Redis 抖动不阻断主流程（最新状态可被下一条覆盖）
        }
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
