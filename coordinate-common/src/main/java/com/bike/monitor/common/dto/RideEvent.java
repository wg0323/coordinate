package com.bike.monitor.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 车辆进出围栏事件（bike-fence-alert topic 载荷）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideEvent {

    public static final String EVENT_ENTER = "ENTER";
    public static final String EVENT_EXIT = "EXIT";

    /** 围栏 ID */
    private Long fenceId;

    /** 围栏名称 */
    private String fenceName;

    /** 车辆 ID */
    private String bikeId;

    /** 事件类型：ENTER / EXIT */
    private String event;

    /** 事发位置（WGS84） */
    private double lng;

    private double lat;

    /** 事件时间戳 epoch millis */
    private long ts;
}
