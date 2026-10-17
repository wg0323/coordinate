package com.bike.monitor.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 电子围栏规则消息（fence-config topic 载荷，api-server 双写 → Flink 广播）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FenceRule {

    public static final String OP_ADD = "ADD";
    public static final String OP_DELETE = "DELETE";

    /** 操作类型：ADD / DELETE */
    private String op;

    /** 围栏 ID */
    private Long fenceId;

    /** 围栏名称 */
    private String name;

    /**
     * 多边形外环坐标，coordinates[0][i] = [lng, lat]，首尾点不要求闭合
     * （闭合由消费方 JTS 处理）。
     */
    private double[][][] coordinates;
}
