package com.bike.monitor.server.controller;

import com.bike.monitor.common.dto.RideEvent;
import com.bike.monitor.server.common.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 告警链路下游（题 10 闭环）：@KafkaListener 消费 bike-fence-alert
 * → 归档 MongoDB alerts 集合 + SSE 实时推送前端弹告警标记。
 */
@Slf4j
@RestController
@RequestMapping("/api/alerts")
@Tag(name = "告警")
public class AlertController {

    private static final String COLLECTION = "alerts";

    private final MongoTemplate mongo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public AlertController(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** 告警归档 + SSE 推送（kafka listener） */
    @KafkaListener(topics = "bike-fence-alert", groupId = "api-server")
    public void onAlert(String message) {
        try {
            RideEvent event = mapper.readValue(message, RideEvent.class);
            Document doc = new Document("fenceId", event.getFenceId())
                    .append("fenceName", event.getFenceName())
                    .append("bikeId", event.getBikeId())
                    .append("event", event.getEvent())
                    .append("lng", event.getLng())
                    .append("lat", event.getLat())
                    .append("ts", event.getTs());
            mongo.save(doc, COLLECTION);
            String payload = mapper.writeValueAsString(event);
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("alert").data(payload));
                } catch (IOException | IllegalStateException e) {
                    emitters.remove(emitter);
                }
            }
        } catch (Exception e) {
            // 单条脏消息不影响后续归档
            log.error("告警消息处理失败: {}", message, e);
        }
    }

    /** SSE 告警流（前端 EventSource 订阅） */
    @Operation(summary = "SSE 告警实时推送流")
    @GetMapping("/stream")
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
    }

    /** 历史告警（最新 50 条） */
    @Operation(summary = "历史告警列表", description = "按时间倒序最新 50 条")
    @GetMapping("/list")
    public ApiResult<List<Document>> list() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "ts")).limit(50);
        List<Document> alerts = new ArrayList<>();
        for (Document doc : mongo.find(query, Document.class, COLLECTION)) {
            doc.remove("_id");
            alerts.add(doc);
        }
        return ApiResult.ok(alerts);
    }
}
