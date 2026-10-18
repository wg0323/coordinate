package com.bike.monitor.server.controller;

import com.bike.monitor.server.common.ApiResult;
import com.bike.monitor.server.dto.NearbyBike;
import com.bike.monitor.server.service.BikeStatusCodec;
import com.bike.monitor.server.service.StatsCalculator;
import com.bike.monitor.server.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 车辆点位检索：附近半径检索 + 视野 BoundingBox 查询。
 */
@RestController
@RequestMapping("/api")
@Tag(name = "附近车辆")
public class NearbyController {

    private static final String GEO_KEY = "bike:latest";
    private static final int MAX_POINTS = 2000;

    private final StringRedisTemplate redis;

    public NearbyController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 附近的车：GEOSEARCH 半径检索，按距离升序 */
    @Operation(summary = "附近的车", description = "Redis GEOSEARCH 半径检索，按距离升序最近 N 辆")
    @GetMapping("/nearby")
    public ApiResult<List<NearbyBike>> nearby(@RequestParam double lng, @RequestParam double lat,
                                              @RequestParam(defaultValue = "1000") double radiusM,
                                              @RequestParam(defaultValue = "50") int count) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(new Point(lng, lat)),
                new Distance(radiusM / 1000.0, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeCoordinates().includeDistance().limit(Math.min(count, MAX_POINTS)).sortAscending());
        List<NearbyBike> bikes = new ArrayList<>();
        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results) {
                RedisGeoCommands.GeoLocation<String> loc = r.getContent();
                Point coord = loc.getPoint();
                bikes.add(new NearbyBike(loc.getName(), r.getDistance().getValue() * 1000,
                        coord.getX(), coord.getY(), "unknown"));
            }
        }
        return ApiResult.ok(bikes);
    }

    /** 视野内点位：GEOSEARCH 圆检索 + bbox 矩形过滤，返回带状态字段 */
    @Operation(summary = "视野内点位", description = "BoundingBox 点位 + 车辆状态（riding/idle/unknown）")
    @GetMapping("/points/bbox")
    public ApiResult<List<NearbyBike>> bbox(@RequestParam double minLng, @RequestParam double minLat,
                                            @RequestParam double maxLng, @RequestParam double maxLat) {
        double centerLng = (minLng + maxLng) / 2;
        double centerLat = (minLat + maxLat) / 2;
        // 包住 bbox 的检索半径（米）：对角线一半 × 纬度换算
        double diagM = Math.hypot((maxLng - minLng) * 85_000, (maxLat - minLat) * 111_000);
        // 注意：limit 过小会造成"以视野中心为圆心的点云"——GEOSEARCH 由中心向外发散，
        // 截断后只剩中心附近的点。视野内点位必须全量返回（BBOX 查询上限放宽到 2 万）；
        // /nearby 的最近 N 辆语义不受影响
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(new Point(centerLng, centerLat)),
                new Distance(diagM / 1000.0, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeCoordinates().limit(20_000));
        List<NearbyBike> bikes = new ArrayList<>();
        if (results != null) {
            long now = System.currentTimeMillis();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results) {
                Point coord = r.getContent().getPoint();
                if (coord.getX() >= minLng && coord.getX() <= maxLng
                        && coord.getY() >= minLat && coord.getY() <= maxLat) {
                    bikes.add(new NearbyBike(r.getContent().getName(), 0, coord.getX(), coord.getY(), "unknown"));
                }
            }
            fillStatus(bikes, now);
        }
        return ApiResult.ok(bikes);
    }

    /** 批量填充状态：HMGET bike:status 后按 5 分钟在线口径推导；hash 缺失保持 unknown */
    private void fillStatus(List<NearbyBike> bikes, long now) {
        if (bikes.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>(bikes.size());
        for (NearbyBike b : bikes) {
            ids.add(b.getBikeId());
        }
        List<Object> values = redis.<String, Object>opsForHash().multiGet(StatsService.STATUS_KEY, ids);
        for (int i = 0; i < bikes.size(); i++) {
            Object v = values != null ? values.get(i) : null;
            BikeStatusCodec.StatusEntry s = v != null ? BikeStatusCodec.decode(v.toString()) : null;
            bikes.get(i).setStatus(StatsCalculator.statusOf(s, now));
        }
    }
}
