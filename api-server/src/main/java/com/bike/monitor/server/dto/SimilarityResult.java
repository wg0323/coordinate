package com.bike.monitor.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 轨迹相似度四指标（题 3）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "轨迹相似度四指标")
public class SimilarityResult {

    @Schema(description = "车辆 A ID")
    private String bikeA;
    @Schema(description = "车辆 B ID")
    private String bikeB;
    @Schema(description = "A 轨迹点数")
    private int pointsA;
    @Schema(description = "B 轨迹点数")
    private int pointsB;
    /** DTW 归一化累积距离（米，越小越相似） */
    private double dtw;
    /** 离散 Fréchet 距离（米，越小越相似） */
    private double frechet;
    /** LCSS 占比（0~1，越大越相似） */
    private double lcss;
    /** EDR 编辑距离（不匹配点数，越小越相似） */
    private int edr;
}
