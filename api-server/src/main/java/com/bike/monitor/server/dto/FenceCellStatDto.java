package com.bike.monitor.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个围栏的装载统计。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FenceCellStatDto {

    @Schema(description = "围栏 ID")
    private Long fenceId;

    @Schema(description = "围栏名称（含容量前缀）")
    private String name;

    @Schema(description = "容量（未设容量为 null）")
    private Integer capacity;

    @Schema(description = "当前围栏内停放车辆数（仅在线已锁车）")
    private int count;

    @Schema(description = "装载率 count/capacity（未设容量为 0）")
    private double loadRate;
}
