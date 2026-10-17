package com.bike.monitor.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 轨迹点（TDengine 查询结果）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "轨迹点")
public class TracePoint {

    @Schema(description = "时间戳（毫秒）")
    private long ts;
    @Schema(description = "经度")
    private double lng;
    @Schema(description = "纬度")
    private double lat;
    @Schema(description = "速度（km/h）")
    private double speed;
}
