package com.bike.monitor.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GPS 原始/清洗后点位消息（Kafka JSON 载荷，全区共用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpsPoint {

    /** 车辆唯一标识 */
    private String bikeId;

    /** 所属区县 */
    private String district;

    /** 经度（WGS84） */
    private double lng;

    /** 纬度（WGS84） */
    private double lat;

    /** 瞬时速度 m/s */
    private double speed;

    /** 航向角（正北 0，顺时针） */
    private float direction;

    /** 是否锁车（静止） */
    private boolean locked;

    /** 上报时间戳 epoch millis */
    private long ts;
}
