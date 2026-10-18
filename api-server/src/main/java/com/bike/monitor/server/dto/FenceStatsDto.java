package com.bike.monitor.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 围栏装载状态统计（大屏围栏概览/着色数据源）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FenceStatsDto {

    @Schema(description = "围栏总数")
    private long total;

    @Schema(description = "正常（装载率 < 0.8 且非空）")
    private long normal;

    @Schema(description = "接近满载（0.8 ≤ 装载率 ≤ 1.0）")
    private long nearFull;

    @Schema(description = "已溢出（装载率 > 1.0）")
    private long overflow;

    @Schema(description = "空置（无车）")
    private long empty;

    @Schema(description = "未设容量（[禁停]/无 [停放N] 前缀）")
    private long noCapacity;

    @Schema(description = "平均装载率（仅有容量的围栏参与，0~1+）")
    private double avgLoadRate;

    @Schema(description = "围栏外停车数（在线已锁车但不在任何围栏内）")
    private long parkedOutside;

    @Schema(description = "溢出围栏列表（按装载率降序）")
    private List<FenceCellStatDto> overflowed;

    @Schema(description = "各围栏统计（fenceId → 单围栏容量/车辆数/装载率），地图着色与详情浮窗用")
    private Map<Long, FenceCellStatDto> cells;
}
