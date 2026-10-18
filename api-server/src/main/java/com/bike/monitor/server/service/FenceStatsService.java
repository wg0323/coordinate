package com.bike.monitor.server.service;

import com.bike.monitor.server.dto.FenceCellStatDto;
import com.bike.monitor.server.dto.FenceStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 围栏装载统计：30s 定时任务全量计算围栏内停放车辆数（STRtree 粗筛 + JTS covers 精筛），
 * 结果存内存缓存供 /api/fence/stats 读取，同时缓存被围栏覆盖的车辆集供 /points/bbox 标注 inFence。
 * 停放口径：bike:status 中在线且已锁车（idle）才计入，骑行中路过围栏不计。
 * 容量来自名称前缀 [停放N]；未设容量（[禁停]/无前缀/容量 0）不参与装载率统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FenceStatsService {

    private static final String COLLECTION = "fences";
    private static final String GEO_KEY = "bike:latest";
    private static final GeometryFactory FACTORY = new GeometryFactory();

    /** 有容量的围栏围栏内计数记录（polygon + capacity + 元信息） */
    private static class FenceRecord {
        final org.locationtech.jts.geom.Polygon polygon;
        final Integer capacity;
        final Long fenceId;
        final String name;
        int count;

        FenceRecord(org.locationtech.jts.geom.Polygon polygon, Integer capacity, Long fenceId, String name) {
            this.polygon = polygon;
            this.capacity = capacity;
            this.fenceId = fenceId;
            this.name = name;
        }
    }

    private final MongoTemplate mongo;
    private final StringRedisTemplate redis;

    private volatile FenceStatsDto cache;

    /** 最近一次计算中被任一围栏覆盖的车辆 ID（不可变快照，供 /points/bbox 标注 inFence） */
    private volatile Set<String> insideFenceIds = Collections.emptySet();

    /** 30s 全量重算 */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void compute() {
        try {
            cache = doCompute();
        } catch (Exception e) {
            log.warn("围栏统计计算失败，保留上次缓存: {}", e.getMessage());
        }
    }

    /** 读取最新统计；首次调用（定时任务未跑）同步触发一次 */
    public FenceStatsDto latest() {
        if (cache == null) {
            compute();
        }
        return cache;
    }

    /** 被任一围栏覆盖的车辆 ID 快照（定时计算产物；首次计算完成前为空集） */
    public Set<String> insideFenceIds() {
        return insideFenceIds;
    }

    @SuppressWarnings("unchecked")
    private FenceStatsDto doCompute() {
        List<Document> docs = mongo.findAll(Document.class, COLLECTION);
        STRtree tree = new STRtree();
        List<FenceRecord> records = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            Document loc = doc.get("location", Document.class);
            if (loc == null) {
                continue;
            }
            List<List<Double>> ring = ((List<List<List<Double>>>) (Object) loc.get("coordinates", List.class)).get(0);
            if (ring.size() < 4) {
                continue; // 退化多边形跳过
            }
            Coordinate[] coords = new Coordinate[ring.size()];
            for (int i = 0; i < ring.size(); i++) {
                coords[i] = new Coordinate(ring.get(i).get(0), ring.get(i).get(1));
            }
            org.locationtech.jts.geom.Polygon polygon;
            try {
                polygon = FACTORY.createPolygon(coords);
            } catch (IllegalArgumentException e) {
                continue; // 非法环（未闭合等）跳过
            }
            FenceRecord record = new FenceRecord(polygon,
                    FenceCapacityParser.parse(doc.getString("name")).orElse(null),
                    doc.getLong("fenceId"), doc.getString("name"));
            int idx = records.size();
            records.add(record);
            tree.insert(polygon.getEnvelopeInternal(), idx);
        }
        tree.build();

        // 停放口径：在线（5 分钟内有上报）且已锁车的车辆
        long now = System.currentTimeMillis();
        Map<String, String> statusHash = redis.<String, String>opsForHash().entries(StatsService.STATUS_KEY);
        Set<String> parkedIds = new HashSet<>(statusHash.size() * 2);
        for (Map.Entry<String, String> e : statusHash.entrySet()) {
            if ("idle".equals(StatsCalculator.statusOf(BikeStatusCodec.decode(e.getValue()), now))) {
                parkedIds.add(e.getKey());
            }
        }

        // 车辆全量位置（北京 300km 大圆覆盖全库成员）
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(new org.springframework.data.geo.Point(116.4, 40.1)),
                new Distance(300, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeCoordinates().limit(300_000));
        Set<String> coveredIds = new HashSet<>();
        Set<String> parkedCoveredIds = new HashSet<>();
        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results) {
                String bikeId = r.getContent().getName();
                boolean parked = parkedIds.contains(bikeId);
                org.springframework.data.geo.Point p = r.getContent().getPoint();
                org.locationtech.jts.geom.Point jtsPoint =
                        FACTORY.createPoint(new Coordinate(p.getX(), p.getY()));
                boolean covered = false;
                for (Object o : tree.query(jtsPoint.getEnvelopeInternal())) {
                    int idx = (Integer) o;
                    FenceRecord record = records.get(idx);
                    if (record.polygon.covers(jtsPoint)) {
                        covered = true;
                        if (parked) {
                            record.count++;
                        }
                    }
                }
                if (covered) {
                    coveredIds.add(bikeId);
                    if (parked) {
                        parkedCoveredIds.add(bikeId);
                    }
                }
            }
        }
        insideFenceIds = Collections.unmodifiableSet(coveredIds);

        // 状态汇总
        long normal = 0, nearFull = 0, overflow = 0, empty = 0, noCapacity = 0;
        double loadSum = 0;
        int loadCount = 0;
        List<FenceCellStatDto> overflowed = new ArrayList<>();
        java.util.Map<Long, FenceCellStatDto> cells = new java.util.HashMap<>(records.size() * 2);
        for (FenceRecord record : records) {
            if (record.capacity == null || record.capacity == 0) {
                noCapacity++;
                cells.put(record.fenceId,
                        new FenceCellStatDto(record.fenceId, record.name, null, record.count, 0.0));
                continue;
            }
            double loadRate = (double) record.count / record.capacity;
            loadSum += Math.min(loadRate, 1.0);
            loadCount++;
            FenceCellStatDto cell =
                    new FenceCellStatDto(record.fenceId, record.name, record.capacity, record.count, loadRate);
            cells.put(record.fenceId, cell);
            if (record.count == 0) {
                empty++;
            } else if (loadRate > 1.0) {
                overflow++;
                overflowed.add(cell);
            } else if (loadRate >= 0.8) {
                nearFull++;
            } else {
                normal++;
            }
        }
        overflowed.sort(Comparator.comparingDouble(FenceCellStatDto::getLoadRate).reversed());
        double avgLoadRate = loadCount > 0 ? loadSum / loadCount : 0.0;
        long parkedOutside = Math.max(0, parkedIds.size() - parkedCoveredIds.size());
        return new FenceStatsDto(records.size(), normal, nearFull, overflow, empty, noCapacity,
                avgLoadRate, parkedOutside, overflowed, cells);
    }
}
