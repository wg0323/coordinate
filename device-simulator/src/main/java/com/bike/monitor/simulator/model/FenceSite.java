package com.bike.monitor.simulator.model;

/**
 * 围栏站点：外环坐标 + 停放容量（名称无 [停放N] 前缀时容量为 null）。
 */
public class FenceSite {

    private final Long fenceId;
    private final String name;
    /** 外环顶点 ring[i] = {lng, lat}，首尾闭合 */
    private final double[][] ring;
    private final Integer capacity;

    public FenceSite(Long fenceId, String name, double[][] ring, Integer capacity) {
        this.fenceId = fenceId;
        this.name = name;
        this.ring = ring;
        this.capacity = capacity;
    }

    public Long getFenceId() {
        return fenceId;
    }

    public String getName() {
        return name;
    }

    public double[][] getRing() {
        return ring;
    }

    public Integer getCapacity() {
        return capacity;
    }
}
