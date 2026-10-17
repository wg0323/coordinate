package com.bike.monitor.server.controller;

import com.bike.monitor.server.common.ApiResult;
import com.bike.monitor.server.dto.SimilarityResult;
import com.bike.monitor.server.dto.TracePoint;
import com.bike.monitor.server.service.TdengineClient;
import com.bike.monitor.server.service.TrajectorySimilarityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 轨迹相似度（题 3）：DTW / Fréchet / LCSS / EDR 四指标。
 * 配合模拟器「伴随车辆」场景可演示套牌车（异常伴随）。
 */
@RestController
@RequestMapping("/api/similarity")
@Tag(name = "轨迹相似度")
public class SimilarityController {

    private final TdengineClient tdengine;
    private final TrajectorySimilarityService similarityService;

    public SimilarityController(TdengineClient tdengine, TrajectorySimilarityService similarityService) {
        this.tdengine = tdengine;
        this.similarityService = similarityService;
    }

    @Operation(summary = "轨迹相似度四指标", description = "DTW / Fréchet / LCSS / EDR（题 3），配合伴随车辆场景演示套牌车")
    @GetMapping
    public ApiResult<SimilarityResult> similarity(@RequestParam String bikeA, @RequestParam String bikeB,
                                                  @RequestParam long from, @RequestParam long to,
                                                  @RequestParam(defaultValue = "200") double epsilonM,
                                                  @RequestParam(defaultValue = "1000") int limit) {
        List<TracePoint> a = tdengine.queryTrace(bikeA, from, to, limit);
        List<TracePoint> b = tdengine.queryTrace(bikeB, from, to, limit);
        if (a.isEmpty() || b.isEmpty()) {
            return ApiResult.error("指定时间窗内某车无轨迹数据");
        }
        SimilarityResult result = new SimilarityResult(bikeA, bikeB, a.size(), b.size(),
                similarityService.dtw(a, b),
                similarityService.frechet(a, b),
                similarityService.lcss(a, b, epsilonM, Math.max(a.size(), b.size())),
                similarityService.edr(a, b, epsilonM));
        return ApiResult.ok(result);
    }
}
