package com.bike.monitor.server.controller;

import com.bike.monitor.server.common.ApiResult;
import com.bike.monitor.server.dto.HeatCellDto;
import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.KeyScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 热力图数据（题 6/11）：读 Redis heat:{windowStart}（H3AggJob 写入），解码 H3 六边形边界。
 */
@RestController
@RequestMapping("/api/heat")
@Tag(name = "热力图")
public class HeatController {

    private static final int MAX_CELLS = 5000;

    private final StringRedisTemplate redis;
    private final H3Core h3;

    public HeatController(StringRedisTemplate redis) throws java.io.IOException {
        this.redis = redis;
        this.h3 = H3Core.newInstance();
    }

    /** 最新已完成窗口的热力网格（按 count 降序，最多 5000 格） */
    @Operation(summary = "热力网格", description = "最新已完成窗口的 H3 六边形（题 6/11）")
    @GetMapping
    public ApiResult<List<HeatCellDto>> heat() {
        // 取窗口序号最大的 heat key（H3AggJob 按事件时间窗口写 key）
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(
                org.springframework.data.redis.core.ScanOptions.scanOptions().match("heat:*").count(1000).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (keys.isEmpty()) {
            return ApiResult.ok(new ArrayList<>());
        }
        String latestKey = keys.stream()
                .max(Comparator.comparingLong(k -> Long.parseLong(k.substring("heat:".length()))))
                .orElse(keys.get(0));
        long windowStart = Long.parseLong(latestKey.substring("heat:".length()));

        Map<Object, Object> entries = redis.opsForHash().entries(latestKey);
        List<HeatCellDto> result = new ArrayList<>();
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            if (result.size() >= MAX_CELLS) {
                break;
            }
            long h3Index = Long.parseLong(String.valueOf(e.getKey()));
            List<LatLng> boundary = h3.cellToBoundary(h3Index);
            List<double[]> ring = new ArrayList<>();
            for (LatLng c : boundary) {
                ring.add(new double[]{c.lng, c.lat});
            }
            result.add(new HeatCellDto(String.valueOf(e.getKey()), windowStart,
                    Integer.parseInt(String.valueOf(e.getValue())), ring));
        }
        result.sort(Comparator.comparingInt(HeatCellDto::getCount).reversed());
        return ApiResult.ok(result);
    }
}
