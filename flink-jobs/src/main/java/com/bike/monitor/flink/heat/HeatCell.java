package com.bike.monitor.flink.heat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热力聚合单元：H3 res8 六边形 → 窗口内 distinct 车辆数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeatCell {

    /** H3 cell 索引（long，序列化为十进制字符串存 Redis） */
    private long h3Index;

    /** 窗口起始时间戳（event time，epoch millis） */
    private long windowStart;

    /** 窗口内 distinct 车辆数 */
    private int count;
}
