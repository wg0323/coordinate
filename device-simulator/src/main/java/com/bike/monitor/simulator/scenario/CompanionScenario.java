package com.bike.monitor.simulator.scenario;

import com.bike.monitor.simulator.model.Bike;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 「伴随车辆」场景注入：选 N 对同区车辆作为领骑/跟骑，
 * 跟骑车辆跟随领骑车的骑行路径（题 3 套牌车/伴随演示的数据来源）。
 */
public class CompanionScenario {

    /** 领骑 bikeId → 跟骑 Bike */
    private final Map<String, Bike> followerByLeader = new HashMap<>();

    /**
     * 从车队中按顺序选取 pairs 对同区车辆（跳过跟骑车重复选用）。
     */
    public void bind(List<Bike> fleet, int pairs, long seed) {
        if (pairs <= 0) {
            return;
        }
        Random random = new Random(seed);
        Map<String, List<Bike>> byDistrict = new HashMap<>();
        for (Bike bike : fleet) {
            byDistrict.computeIfAbsent(bike.getDistrict().getName(), k -> new ArrayList<>()).add(bike);
        }
        int bound = 0;
        for (List<Bike> bikes : byDistrict.values()) {
            if (bikes.size() < 2 || bound >= pairs) {
                continue;
            }
            for (int i = 0; i + 1 < bikes.size() && bound < pairs; i += 2) {
                Bike leader = bikes.get(i);
                Bike follower = bikes.get(i + 1);
                followerByLeader.put(leader.getBikeId(), follower);
                bound++;
            }
        }
    }

    public Bike followerOf(Bike leader) {
        return followerByLeader.get(leader.getBikeId());
    }

    /** 是否为某领骑车的跟骑车辆 */
    public boolean isFollower(Bike bike) {
        return followerByLeader.containsValue(bike);
    }
}
