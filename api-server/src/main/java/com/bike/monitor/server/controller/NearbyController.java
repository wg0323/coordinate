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
import java.util.Map;

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

    /** 视野内点位：只回传 bike:status 中有记录的车辆（当前运行正在上报的车辆）。
     * 车辆位置按状态成员批量取 GEO 坐标——不走 GEOSEARCH 的距离截断，避免历史残留位置多时
     * 只截出“以视野中心为圆心”的一团点。status hash 整体缺失时降级为 GEOSEARCH + unknown。 */
    @Operation(summary = "视野内点位", description = "BoundingBox 点位 + 车辆状态（riding/idle/unknown）")
    @GetMapping("/points/bbox")
    public ApiResult<List<NearbyBike>> bbox(@RequestParam double minLng, @RequestParam double minLat,
                                            @RequestParam double maxLng, @RequestParam double maxLat) {
        long now = System.currentTimeMillis();
        Map<String, String> statusHash = redis.<String, String>opsForHash().entries(StatsService.STATUS_KEY);
        if (statusHash.isEmpty()) {
            return ApiResult.ok(bboxFallback(minLng, minLat, maxLng, maxLat));
        }
        List<String> ids = new ArrayList<>(statusHash.keySet());
        List<Point> positions = redis.opsForGeo().position(GEO_KEY, ids.toArray(new String[0]));
        return ApiResult.ok(toNearbyBikes(ids, positions, statusHash,
                minLng, minLat, maxLng, maxLat, now));
    }

    /** 坐标 + 状态 → 视野内点位（纯函数，便于单测）：GEOPOS 结果顺序与 ids 一一对应 */
    static List<NearbyBike> toNearbyBikes(List<String> ids, List<Point> positions,
                                          Map<String, String> statusHash,
                                          double minLng, double minLat, double maxLng, double maxLat,
                                          long now) {
        List<NearbyBike> bikes = new ArrayList<>();
        if (positions == null) {
            return bikes;
        }
        for (int i = 0; i < ids.size() && i < positions.size(); i++) {
            Point p = positions.get(i);
            if (p == null || p.getX() < minLng || p.getX() > maxLng
                    || p.getY() < minLat || p.getY() > maxLat) {
                continue;
            }
            String bikeId = ids.get(i);
            bikes.add(new NearbyBike(bikeId, 0, p.getX(), p.getY(),
                    StatsCalculator.statusOf(BikeStatusCodec.decode(statusHash.get(bikeId)), now)));
        }
        return bikes;
    }

    /** 降级路径：bike:status 未就绪时按 GEOSEARCH 视野检索，状态统一 unknown（不白屏） */
    private List<NearbyBike> bboxFallback(double minLng, double minLat, double maxLng, double maxLat) {
        double centerLng = (minLng + maxLng) / 2;
        double centerLat = (minLat + maxLat) / 2;
        // 包住 bbox 的检索半径（米）：对角线一半 × 纬度换算
        double diagM = Math.hypot((maxLng - minLng) * 85_000, (maxLat - minLat) * 111_000);
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(new Point(centerLng, centerLat)),
                new Distance(diagM / 1000.0, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeCoordinates().limit(20_000));
        List<NearbyBike> bikes = new ArrayList<>();
        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results) {
                Point coord = r.getContent().getPoint();
                if (coord.getX() >= minLng && coord.getX() <= maxLng
                        && coord.getY() >= minLat && coord.getY() <= maxLat) {
                    bikes.add(new NearbyBike(r.getContent().getName(), 0, coord.getX(), coord.getY(), "unknown"));
                }
            }
        }
        return bikes;
    }
}
