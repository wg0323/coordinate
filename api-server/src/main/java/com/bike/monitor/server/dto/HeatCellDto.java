package com.bike.monitor.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 热力六边形（H3 res8）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "热力六边形（H3 res8，1 分钟窗口）")
public class HeatCellDto {

    /** H3 cell 索引（十进制字符串，前端无需解析 long） */
    @Schema(description = "H3 cell 索引（十进制字符串）")
    private String h3Index;
    @Schema(description = "窗口起始时间戳（毫秒）")
    private long windowStart;
    @Schema(description = "窗口内 distinct 车辆数")
    private int count;
    /** 六边形边界顶点 [[lng,lat],...]（GeoJSON 顺序） */
    @Schema(description = "六边形边界顶点 [[lng,lat],...]")
    private List<double[]> boundary;
}
