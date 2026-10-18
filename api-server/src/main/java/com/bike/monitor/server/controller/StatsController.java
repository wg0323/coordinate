package com.bike.monitor.server.controller;

import com.bike.monitor.server.common.ApiResult;
import com.bike.monitor.server.dto.StatsDto;
import com.bike.monitor.server.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大屏全局统计指标。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "全局统计")
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "车辆统计概览", description = "总数/在线/骑行中/空闲/离线/各区在线数（大屏核心指标）")
    @GetMapping("/stats")
    public ApiResult<StatsDto> stats() {
        return ApiResult.ok(statsService.stats());
    }
}
