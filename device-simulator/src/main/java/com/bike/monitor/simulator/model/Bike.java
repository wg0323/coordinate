package com.bike.monitor.simulator.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 车辆运行时状态：静态属性 + 骑行/心跳事件推进所需的运行时字段。
 */
@Getter
public class Bike {

    private final String bikeId;
    private final District district;
    /** 常驻点（锁车停留位置） */
    private final double homeLng;
    private final double homeLat;

    /** 状态代次：伴随时作废旧事件用 */
    @Setter
    private volatile int generation = 0;

    @Setter
    private volatile Ride currentRide = null;

    public Bike(String bikeId, District district, double homeLng, double homeLat) {
        this.bikeId = bikeId;
        this.district = district;
        this.homeLng = homeLng;
        this.homeLat = homeLat;
    }

    public boolean isRiding() {
        return currentRide != null;
    }
}
