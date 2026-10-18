package com.bike.monitor.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 附近车辆（Redis GEO 检索结果）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "附近车辆点位")
public class NearbyBike {

    @Schema(description = "车辆 ID")
    private String bikeId;
    /** 距检索点距离（米） */
    @Schema(description = "距检索点距离（米）；/points/bbox 恒为 0")
    private double distM;
    @Schema(description = "经度")
    private double lng;
    @Schema(description = "纬度")
    private double lat;
    /** 车辆状态：riding=骑行中 idle=空闲 unknown=无状态数据（/nearby 恒为 unknown） */
    @Schema(description = "车辆状态 riding|idle|unknown；/nearby 恒为 unknown")
    private String status;
}
