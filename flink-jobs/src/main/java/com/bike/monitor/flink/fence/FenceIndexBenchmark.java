package com.bike.monitor.flink.fence;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 题 9 基准（简版，非 JMH 全量基线）：10 万不规则围栏下单点判定耗时。
 * 验证 STRtree MBR 粗筛 O(logN) + JTS covers 精筛 相对全量遍历的加速比。
 * 运行：java -cp flink-jobs.jar com.bike.monitor.flink.fence.FenceIndexBenchmark [围栏数] [查询点数]
 */
public class FenceIndexBenchmark {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    public static void main(String[] args) {
        int fenceCount = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        int queryCount = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        Random random = new Random(42L);

        // 构造随机不规则多边形（6~10 边，中心散布在北京区域）
        System.out.printf("构建 %d 个随机多边形...%n", fenceCount);
        long buildStart = System.currentTimeMillis();
        List<Polygon> fences = new ArrayList<>(fenceCount);
        for (int i = 0; i < fenceCount; i++) {
            fences.add(randomPolygon(random));
        }
        STRtree index = new STRtree();
        for (int i = 0; i < fenceCount; i++) {
            index.insert(fences.get(i).getEnvelopeInternal(), i);
        }
        index.build();
        System.out.printf("STRtree 构建耗时 %d ms%n", System.currentTimeMillis() - buildStart);

        // 正确性抽查：STRtree 粗筛+精筛 必须与全量遍历 covers 结果一致
        int mismatches = 0;
        for (int i = 0; i < 1000; i++) {
            Point p = randomPoint(random);
            boolean indexed = index.query(new Envelope(p.getCoordinate())).stream()
                    .anyMatch(id -> fences.get((Integer) id).covers(p));
            boolean brute = fences.stream().anyMatch(f -> f.covers(p));
            if (indexed != brute) {
                mismatches++;
            }
        }
        System.out.printf("正确性抽查 1000 点，不一致 %d 个%n", mismatches);

        // 基准：STRtree 粗筛+精筛
        for (int i = 0; i < 20_000; i++) {
            query(index, fences, random);
        }
        long start = System.currentTimeMillis();
        int hits = 0;
        for (int i = 0; i < queryCount; i++) {
            hits += query(index, fences, random) ? 1 : 0;
        }
        long indexedMs = System.currentTimeMillis() - start;

        // 基准：全量遍历（对照组，1/10 查询量避免跑太久）
        int bruteCount = Math.max(1, queryCount / 10);
        start = System.currentTimeMillis();
        for (int i = 0; i < bruteCount; i++) {
            Point p = randomPoint(random);
            for (Polygon f : fences) {
                if (f.covers(p)) {
                    break;
                }
            }
        }
        long bruteMs = System.currentTimeMillis() - start;

        System.out.printf("STRtree：%d 次查询 %d ms（%.2f µs/次，命中 %d，%.0f QPS）%n",
                queryCount, indexedMs, indexedMs * 1000.0 / queryCount, hits, queryCount * 1000.0 / indexedMs);
        System.out.printf("全量遍历：%d 次查询 %d ms（%.2f µs/次，%.0f QPS）%n",
                bruteCount, bruteMs, bruteMs * 1000.0 / bruteCount, bruteCount * 1000.0 / bruteMs);
        System.out.printf("加速比 ≈ %.0f 倍%n",
                (bruteMs * 1000.0 / bruteCount) / (indexedMs * 1000.0 / queryCount));
    }

    private static boolean query(STRtree index, List<Polygon> fences, Random random) {
        Point p = randomPoint(random);
        for (Object id : index.query(new Envelope(p.getCoordinate()))) {
            if (fences.get((Integer) id).covers(p)) {
                return true;
            }
        }
        return false;
    }

    private static Point randomPoint(Random random) {
        return FACTORY.createPoint(new Coordinate(
                116.0 + random.nextDouble() * 1.2,
                39.6 + random.nextDouble() * 0.9));
    }

    private static Polygon randomPolygon(Random random) {
        double cx = 116.0 + random.nextDouble() * 1.2;
        double cy = 39.6 + random.nextDouble() * 0.9;
        double radius = 0.002 + random.nextDouble() * 0.004;
        int sides = 6 + random.nextInt(5);
        Coordinate[] coords = new Coordinate[sides + 1];
        for (int i = 0; i < sides; i++) {
            double angle = 2 * Math.PI * i / sides;
            double r = radius * (0.6 + random.nextDouble() * 0.8);
            coords[i] = new Coordinate(cx + r * Math.cos(angle), cy + r * Math.sin(angle));
        }
        coords[sides] = coords[0];
        return FACTORY.createPolygon(coords);
    }
}
