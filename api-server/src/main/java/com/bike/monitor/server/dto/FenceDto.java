package com.bike.monitor.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 围栏创建请求 / 查询响应。
 */
@Data
@NoArgsConstructor
@Schema(description = "电子围栏")
public class FenceDto {

    @Schema(description = "围栏 ID（创建时可不传，服务端分配）")
    private Long fenceId;
    @Schema(description = "围栏名称")
    private String name;
    /** 多边形顶点 [[lng,lat],...]（不要求闭合） */
    @Schema(description = "多边形顶点 [[lng,lat],...]，至少 3 个，不要求闭合")
    private List<List<Double>> coordinates;
}
