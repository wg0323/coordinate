package com.bike.monitor.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 全局车辆统计（大屏核心指标数据源）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatsDto {

    @Schema(description = "车队台账总数（Mongo bikes）")
    private long total;

    @Schema(description = "在线车辆数（5 分钟内有上报）")
    private long online;

    @Schema(description = "骑行中（在线且未锁车）")
    private long riding;

    @Schema(description = "空闲可租（在线且已锁车）")
    private long idle;

    @Schema(description = "离线车辆数（台账 - 在线）")
    private long offline;

    @Schema(description = "在线率 0~1")
    private double onlineRate;

    @Schema(description = "各区在线车辆数")
    private List<DistrictCountDto> districtCounts;
}
