package com.bike.monitor.server.controller;

import com.bike.monitor.server.common.ApiResult;
import com.bike.monitor.server.dto.NearbyBike;
import com.bike.monitor.server.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 视野点位：只回传 bike:status 中有记录的车辆（避免历史残留位置成团），
 * status hash 缺失时降级为 GEOSEARCH（连不到数据也不报错）。
 */
class NearbyControllerTest {

    private static final long NOW = 1_000_000_000L;
    private static final String TS = String.valueOf(NOW - 1_000);

    @Test
    void should_return_only_bikes_inside_bbox() {
        List<String> ids = Arrays.asList("b1", "b2");
        List<Point> positions = Arrays.asList(
                new Point(116.40, 39.90),       // 视野内
                new Point(117.00, 40.50));      // 视野外
        Map<String, String> status = new LinkedHashMap<>();
        status.put("b1", "1,中心城区," + TS);   // 空闲
        status.put("b2", "0,通州," + TS);       // 骑行中

        List<NearbyBike> bikes = NearbyController.toNearbyBikes(
                ids, positions, status, 116.30, 39.80, 116.50, 39.95, NOW);

        assertEquals(1, bikes.size());
        assertEquals("b1", bikes.get(0).getBikeId());
        assertEquals("idle", bikes.get(0).getStatus());
    }

    @Test
    void should_skip_bikes_without_geo_position() {
        List<String> ids = Arrays.asList("b1", "b2");
        List<Point> positions = Arrays.asList(null, new Point(116.40, 39.90));
        Map<String, String> status = new LinkedHashMap<>();
        status.put("b1", "0,中心城区," + TS);
        status.put("b2", "1,中心城区," + TS);

        List<NearbyBike> bikes = NearbyController.toNearbyBikes(
                ids, positions, status, 116.30, 39.80, 116.50, 39.95, NOW);

        assertEquals(1, bikes.size());
        assertEquals("b2", bikes.get(0).getBikeId());
        assertEquals("idle", bikes.get(0).getStatus());
    }

    @Test
    void should_return_empty_when_status_hash_missing_and_geo_empty() {
        // 降级路径：search 未 stub 时 Mockito 返回 null，不应抛异常
        NearbyController controller = controller(new HashMap<>());

        ApiResult<List<NearbyBike>> result = controller.bbox(116.30, 39.80, 116.50, 39.95);

        assertTrue(result.getData().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static NearbyController controller(Map<String, String> status) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, String, String> hash = mock(HashOperations.class);
        GeoOperations<String, String> geo = mock(GeoOperations.class);
        when(redis.<String, String>opsForHash()).thenReturn(hash);
        when(redis.opsForGeo()).thenReturn(geo);
        when(hash.entries(StatsService.STATUS_KEY)).thenReturn(status);
        return new NearbyController(redis);
    }
}
