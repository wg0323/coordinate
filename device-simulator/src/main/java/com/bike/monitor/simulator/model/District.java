package com.bike.monitor.simulator.model;

import java.util.Arrays;
import java.util.List;

/**
 * 12 区县静态建模表（design.md 5.1，1:10 缩放）。
 * 中心坐标与分布半宽为演示用近似值，覆盖各区行政中心附近区域。
 */
public enum District {

    CENTER_CITY("中心城区", "XC", 67000, 3.80, 116.40, 39.90, 0.09, 0.06),
    TONGZHOU("通州", "TZ", 4050, 3.24, 116.65, 39.90, 0.05, 0.03),
    MENTOUGOU("门头沟", "MTG", 470, 2.05, 116.10, 39.94, 0.03, 0.02),
    FANGSHAN("房山", "FS", 1700, 2.66, 116.14, 39.72, 0.04, 0.03),
    DAXING("大兴", "DX", 1330, 5.04, 116.34, 39.73, 0.04, 0.02),
    SHUNYI("顺义", "SY", 2400, 1.93, 116.65, 40.13, 0.04, 0.02),
    CHANGPING("昌平", "CP", 3690, 4.64, 116.23, 40.22, 0.04, 0.03),
    PINGGU("平谷", "PG", 600, 1.03, 117.12, 40.14, 0.02, 0.01),
    HUAIROU("怀柔", "HR", 600, 0.90, 116.63, 40.32, 0.02, 0.01),
    MIYUN("密云", "MY", 550, 0.62, 116.84, 40.37, 0.02, 0.01),
    YANQING("延庆", "YQ", 580, 0.84, 115.97, 40.46, 0.02, 0.01),
    JINGKAIQU("经开区", "JKQ", 1870, 2.60, 116.50, 39.79, 0.03, 0.02);

    private final String name;
    private final String code;
    private final int vehicles;
    private final double turnover;
    private final double lng;
    private final double lat;
    private final double lngSpan;
    private final double latSpan;

    District(String name, String code, int vehicles, double turnover,
             double lng, double lat, double lngSpan, double latSpan) {
        this.name = name;
        this.code = code;
        this.vehicles = vehicles;
        this.turnover = turnover;
        this.lng = lng;
        this.lat = lat;
        this.lngSpan = lngSpan;
        this.latSpan = latSpan;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public int getVehicles() {
        return vehicles;
    }

    public double getTurnover() {
        return turnover;
    }

    public double getLng() {
        return lng;
    }

    public double getLat() {
        return lat;
    }

    public double getLngSpan() {
        return lngSpan;
    }

    public double getLatSpan() {
        return latSpan;
    }

    public static List<District> all() {
        return Arrays.asList(values());
    }

    public static District byCode(String code) {
        for (District district : values()) {
            if (district.code.equals(code)) {
                return district;
            }
        }
        return null;
    }
}
