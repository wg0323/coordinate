package com.bike.monitor.server.controller;

import com.bike.monitor.server.common.ApiResult;
import com.bike.monitor.server.dto.FenceStatsDto;
import com.bike.monitor.server.service.FenceStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 围栏装载状态统计（大屏围栏概览与着色数据源）。
 */
@RestController
@RequestMapping("/api/fence")
@RequiredArgsConstructor
@Tag(name = "围栏统计")
public class FenceStatsController {

    private final FenceStatsService fenceStatsService;

    @Operation(summary = "围栏装载统计", description = "正常/接近满载/溢出/空置/未设容量计数 + 平均装载率 + 溢出列表（30s 定时刷新）")
    @GetMapping("/stats")
    public ApiResult<FenceStatsDto> stats() {
        return ApiResult.ok(fenceStatsService.latest());
    }
}
