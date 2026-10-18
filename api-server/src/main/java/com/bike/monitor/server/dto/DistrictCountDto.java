package com.bike.monitor.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 区县维度车辆计数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistrictCountDto {

    @Schema(description = "区县名")
    private String name;

    @Schema(description = "该区当前在线车辆数")
    private long count;

    @Schema(description = "该区当前骑行中车辆数")
    private long riding;
}
