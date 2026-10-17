package com.bike.monitor.simulator.store;

import com.bike.monitor.simulator.model.Bike;
import com.bike.monitor.simulator.model.District;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 车队台账的 MongoDB 存取（集合 bikes，库由连接串指定）。
 * 首次启动由模拟器将全量车队 seed 入库，此后每次启动从库中加载——
 * 对应真实项目里"车辆台账已由资产/运营系统落库，模拟器只负责模拟设备上报"。
 */
public class FleetStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FleetStore.class);

    private static final String COLLECTION = "bikes";
    private static final int BATCH_SIZE = 1000;

    private final MongoClient client;
    private final MongoCollection<Document> collection;

    public FleetStore(String uri) {
        ConnectionString cs = new ConnectionString(uri);
        if (cs.getDatabase() == null) {
            throw new IllegalArgumentException("Mongo 连接串必须指定库名，如 mongodb://host:27017/bike_monitor");
        }
        this.client = MongoClients.create(cs);
        MongoDatabase db = client.getDatabase(cs.getDatabase());
        this.collection = db.getCollection(COLLECTION);
        this.collection.createIndex(Indexes.ascending("bikeId"),
                new IndexOptions().unique(true));
    }

    public long count() {
        return collection.countDocuments();
    }

    public void saveAll(List<Bike> fleet) {
        List<Document> batch = new ArrayList<>(BATCH_SIZE);
        for (Bike bike : fleet) {
            batch.add(toDocument(bike));
            if (batch.size() == BATCH_SIZE) {
                collection.insertMany(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            collection.insertMany(batch);
        }
    }

    public List<Bike> loadAll() {
        List<Bike> fleet = new ArrayList<>();
        for (Document doc : collection.find()) {
            District district = District.byCode(doc.getString("districtCode"));
            if (district == null) {
                log.warn("未知区县编码，跳过该车辆：bikeId={}, districtCode={}",
                        doc.getString("bikeId"), doc.getString("districtCode"));
                continue;
            }
            fleet.add(new Bike(doc.getString("bikeId"), district,
                    doc.getDouble("homeLng"), doc.getDouble("homeLat")));
        }
        return fleet;
    }

    private Document toDocument(Bike bike) {
        return new Document("bikeId", bike.getBikeId())
                .append("districtCode", bike.getDistrict().getCode())
                .append("districtName", bike.getDistrict().getName())
                .append("homeLng", bike.getHomeLng())
                .append("homeLat", bike.getHomeLat());
    }

    @Override
    public void close() {
        client.close();
    }
}
