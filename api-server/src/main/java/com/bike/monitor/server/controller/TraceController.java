package com.bike.monitor.server.controller;

import com.bike.monitor.server.common.ApiResult;
import com.bike.monitor.server.dto.TracePoint;
import com.bike.monitor.server.service.TdengineClient;
import com.bike.monitor.server.service.TraceSimplifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轨迹查询 + DP 抽稀（题 2）：同一条轨迹按阈值抽稀前后点数对比。
 */
@RestController
@RequestMapping("/api/trace")
@Tag(name = "轨迹查询")
public class TraceController {

    private final TdengineClient tdengine;
    private final TraceSimplifyService simplifyService;

    public TraceController(TdengineClient tdengine, TraceSimplifyService simplifyService) {
        this.tdengine = tdengine;
        this.simplifyService = simplifyService;
    }

    @Operation(summary = "轨迹查询 + DP 抽稀", description = "原始点数与抽稀后点数对比（题 2）")
    @GetMapping
    public ApiResult<Map<String, Object>> trace(@RequestParam String bikeId,
                                                @RequestParam long from,
                                                @RequestParam long to,
                                                @RequestParam(defaultValue = "0.0001") double threshold,
                                                @RequestParam(defaultValue = "5000") int limit) {
        List<TracePoint> raw = tdengine.queryTrace(bikeId, from, to, limit);
        List<TracePoint> self = simplifyService.douglasPeucker(raw, threshold);
        List<TracePoint> jts = simplifyService.jtsDouglasPeucker(raw, threshold);

        Map<String, Object> data = new HashMap<>();
        data.put("bikeId", bikeId);
        data.put("rawCount", raw.size());
        data.put("simplifiedCount", self.size());
        data.put("jtsSimplifiedCount", jts.size());
        data.put("raw", raw);
        data.put("simplified", self);
        return ApiResult.ok(data);
    }
}
