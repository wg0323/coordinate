package com.bike.monitor.server.controller;

import com.bike.monitor.common.dto.FenceRule;
import com.bike.monitor.server.common.ApiResult;
import com.bike.monitor.server.dto.FenceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 围栏 CRUD（题 7/10）：MongoDB（2dsphere）落库 + **业务双写 Kafka fence-config** 广播给 FenceJob。
 * （生产主流为 Debezium CDC，本机单机 MongoDB 无副本集不支持 Change Streams，见 design.md 5.3）
 */
@Slf4j
@RestController
@RequestMapping("/api/fence")
@Tag(name = "电子围栏")
public class FenceController {

    private static final String COLLECTION = "fences";
    private static final String TOPIC = "fence-config";

    private final MongoTemplate mongo;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();
    private final java.util.concurrent.atomic.AtomicLong idSeed =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() % 1_000_000 * 1000);

    public FenceController(MongoTemplate mongo, KafkaTemplate<String, String> kafka) {
        this.mongo = mongo;
        this.kafka = kafka;
        // 2dsphere 索引（幂等）
        mongo.indexOps(COLLECTION)
                .ensureIndex(new org.springframework.data.mongodb.core.index.GeospatialIndex("location")
                        .typed(org.springframework.data.mongodb.core.index.GeoSpatialIndexType.GEO_2DSPHERE));
    }

    @Operation(summary = "创建围栏", description = "MongoDB 落库 + 业务双写 Kafka fence-config 广播给 FenceJob")
    @PostMapping
    public ApiResult<FenceDto> create(@RequestBody FenceDto dto) {
        if (dto.getCoordinates() == null || dto.getCoordinates().size() < 3) {
            return ApiResult.error("多边形至少需要 3 个顶点");
        }
        long fenceId = dto.getFenceId() != null ? dto.getFenceId() : idSeed.incrementAndGet();
        dto.setFenceId(fenceId);
        Document doc = new Document("fenceId", fenceId)
                .append("name", dto.getName())
                .append("location", toGeoJson(dto.getCoordinates()));
        mongo.save(doc, COLLECTION);
        sendRule(FenceRule.OP_ADD, dto);
        log.info("围栏已创建 fenceId={} name={} 顶点数={}", fenceId, dto.getName(), dto.getCoordinates().size());
        return ApiResult.ok(dto);
    }

    @Operation(summary = "删除围栏", description = "MongoDB 删除 + Kafka 广播 REMOVE")
    @DeleteMapping("/{fenceId}")
    public ApiResult<Void> delete(@PathVariable long fenceId) {
        mongo.remove(Query.query(Criteria.where("fenceId").is(fenceId)), COLLECTION);
        FenceDto dto = new FenceDto();
        dto.setFenceId(fenceId);
        sendRule(FenceRule.OP_DELETE, dto);
        log.info("围栏已删除 fenceId={}", fenceId);
        return ApiResult.ok();
    }

    @Operation(summary = "围栏列表", description = "全量返回（当前约 6917 条，前端 Canvas 视野裁剪渲染）")
    @GetMapping("/list")
    public ApiResult<List<FenceDto>> list() {
        List<FenceDto> fences = new ArrayList<>();
        for (Document doc : mongo.findAll(Document.class, COLLECTION)) {
            FenceDto dto = new FenceDto();
            dto.setFenceId(doc.getLong("fenceId"));
            dto.setName(doc.getString("name"));
            Document loc = doc.get("location", Document.class);
            if (loc != null) {
                List<List<Double>> coords = loc.get("coordinates", List.class);
                List<List<Double>> ring = new ArrayList<>();
                for (List<Double> c : (List<List<Double>>) (Object) coords.get(0)) {
                    ring.add(java.util.Arrays.asList(c.get(0), c.get(1)));
                }
                dto.setCoordinates(ring);
            }
            fences.add(dto);
        }
        return ApiResult.ok(fences);
    }

    /** $geoIntersects 对照（题 7：MongoDB 空间关系查询演示） */
    @Operation(summary = "空间相交查询", description = "$geoIntersects：给定经纬度返回包含该点的围栏")
    @GetMapping("/intersects")
    public ApiResult<List<FenceDto>> intersects(@RequestParam double lng, @RequestParam double lat) {
        Query query = Query.query(Criteria.where("location").intersects(
                new org.springframework.data.mongodb.core.geo.GeoJsonPoint(lng, lat)));
        List<Long> ids = new ArrayList<>();
        for (Document doc : mongo.find(query, Document.class, COLLECTION)) {
            ids.add(doc.getLong("fenceId"));
        }
        return ApiResult.ok(list().getData().stream()
                .filter(f -> ids.contains(f.getFenceId())).collect(Collectors.toList()));
    }

    private Document toGeoJson(List<List<Double>> coords) {
        List<List<Double>> ring = new ArrayList<>(coords);
        // GeoJSON 环要求闭合
        List<Double> first = coords.get(0);
        List<Double> last = coords.get(coords.size() - 1);
        if (!first.get(0).equals(last.get(0)) || !first.get(1).equals(last.get(1))) {
            ring.add(java.util.Arrays.asList(first.get(0), first.get(1)));
        }
        List<Object> geoRing = new ArrayList<>();
        for (List<Double> c : ring) {
            geoRing.add(java.util.Arrays.asList(c.get(0), c.get(1)));
        }
        return new Document("type", "Polygon")
                .append("coordinates", java.util.Collections.singletonList(geoRing));
    }

    /** 业务双写：落库成功后发 fence-config（FenceJob 广播消费） */
    private void sendRule(String op, FenceDto dto) {
        try {
            FenceRule rule = new FenceRule();
            rule.setOp(op);
            rule.setFenceId(dto.getFenceId());
            rule.setName(dto.getName());
            if (dto.getCoordinates() != null) {
                double[][][] coords = new double[1][dto.getCoordinates().size()][2];
                for (int i = 0; i < dto.getCoordinates().size(); i++) {
                    coords[0][i][0] = dto.getCoordinates().get(i).get(0);
                    coords[0][i][1] = dto.getCoordinates().get(i).get(1);
                }
                rule.setCoordinates(coords);
            }
            kafka.send(TOPIC, String.valueOf(dto.getFenceId()), mapper.writeValueAsString(rule));
        } catch (Exception e) {
            // 广播失败不影响落库（FenceJob 重启会全量读 earliest）
        }
    }
}
