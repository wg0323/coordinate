package com.bike.monitor.simulator.generator;

import com.bike.monitor.common.geo.RayCasting;
import com.bike.monitor.simulator.model.Bike;
import com.bike.monitor.simulator.model.FenceSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 围栏停放分配：把活跃车的常驻点迁入部分有容量围栏，使大屏上能看到"围栏内停放"的场景。
 *
 * <p>目标按"围栏内停放车辆数"折算：停放比例约 64%，故迁入车辆数 = 目标 / 0.64。
 * 单栏按容量 40% 填充（容量 49 → 20 辆、97 → 39 辆），只覆盖部分围栏，其余保持空置。</p>
 */
public class FenceParkingPlanner {

    private static final Logger log = LoggerFactory.getLogger(FenceParkingPlanner.class);

    /** 在线车辆中停放状态占比（经验值，用于把目标停放数换算成需迁入围栏的活跃车数） */
    private static final double IDLE_RATIO = 0.64;
    /** 单栏填充其容量的比例 */
    private static final double FILL_RATIO = 0.4;
    /** 单栏至少分配的车辆数 */
    private static final int MIN_PER_FENCE = 10;
    /** 单栏分配上限（与"大围栏 30~50 辆"的预期对齐，避免超大围栏堆出 80+） */
    private static final int MAX_PER_FENCE = 50;
    /** 围栏内随机取点失败后的重试次数 */
    private static final int MAX_POINT_ATTEMPTS = 30;

    private final Random random;

    public FenceParkingPlanner(long seed) {
        this.random = new Random(seed);
    }

    /**
     * 将常驻点分配到围栏内。
     *
     * @return 实际迁入的车辆数
     */
    public int assign(List<Bike> fleet, List<FenceSite> fences, int targetParked) {
        if (targetParked <= 0 || fleet.isEmpty()) {
            return 0;
        }
        List<FenceSite> eligible = new ArrayList<>();
        for (FenceSite fence : fences) {
            if (fence.getCapacity() != null && fence.getCapacity() >= MIN_PER_FENCE) {
                eligible.add(fence);
            }
        }
        if (eligible.isEmpty()) {
            log.warn("围栏常驻点分配跳过：无容量 >= {} 的围栏", MIN_PER_FENCE);
            return 0;
        }
        // 固定种子 + fenceId 排序保证同一次台账下分配结果可复现
        Collections.sort(eligible, Comparator.comparing(FenceSite::getFenceId));
        Collections.shuffle(eligible, random);
        List<Bike> candidates = new ArrayList<>(fleet);
        Collections.shuffle(candidates, random);

        int want = Math.min(candidates.size(), (int) Math.ceil(targetParked / IDLE_RATIO));
        int cursor = 0;
        int usedFences = 0;
        int minCount = Integer.MAX_VALUE;
        int maxCount = 0;
        for (FenceSite fence : eligible) {
            if (cursor >= want) {
                break;
            }
            int perFence = Math.min(fence.getCapacity(), MAX_PER_FENCE);
            perFence = Math.min(perFence, Math.max(MIN_PER_FENCE, (int) Math.round(fence.getCapacity() * FILL_RATIO)));
            perFence = Math.min(perFence, want - cursor);
            for (int i = 0; i < perFence; i++) {
                double[] point = randomPointInside(fence.getRing());
                Bike bike = candidates.get(cursor++);
                bike.setHomeLng(point[0]);
                bike.setHomeLat(point[1]);
            }
            usedFences++;
            minCount = Math.min(minCount, perFence);
            maxCount = Math.max(maxCount, perFence);
        }
        if (cursor < want) {
            log.warn("可用围栏容量不足：计划迁入 {} 辆，实际 {} 辆", want, cursor);
        }
        log.info("围栏常驻点分配：目标停放 {} 辆，迁入车辆 {} 辆，覆盖围栏 {} 个，单栏 {}-{} 辆",
                targetParked, cursor, usedFences, usedFences == 0 ? 0 : minCount, maxCount);
        return cursor;
    }

    /**
     * 在围栏外环内取一个随机点：边线随机取点后向质心内缩 30%~70%，
     * 再用射线法校验；失败则回退到质心。细长围栏靠"内缩"避免落在边界上。
     */
    private double[] randomPointInside(double[][] ring) {
        int n = ring.length;
        double cx = 0;
        double cy = 0;
        for (double[] p : ring) {
            cx += p[0];
            cy += p[1];
        }
        cx /= n;
        cy /= n;
        for (int attempt = 0; attempt < MAX_POINT_ATTEMPTS; attempt++) {
            int i = random.nextInt(n);
            int j = (i + 1) % n;
            double t = random.nextDouble();
            double px = ring[i][0] + (ring[j][0] - ring[i][0]) * t;
            double py = ring[i][1] + (ring[j][1] - ring[i][1]) * t;
            double u = 0.3 + random.nextDouble() * 0.4;
            double qx = px + (cx - px) * u;
            double qy = py + (cy - py) * u;
            if (RayCasting.contains(ring, qx, qy)) {
                return new double[]{qx, qy};
            }
        }
        return new double[]{cx, cy};
    }
}
