package com.bike.monitor.simulator.store;

import com.bike.monitor.simulator.model.FenceSite;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 电子围栏 MongoDB 读取（集合 fences，库由连接串指定）。
 * 容量口径与 api-server 的 FenceCapacityParser 一致：名称前缀 [停放N]。
 */
public class FenceStore implements AutoCloseable {

    private static final String COLLECTION = "fences";
    private static final Pattern CAPACITY = Pattern.compile("^\\[停放(\\d+)\\]");

    private final MongoClient client;
    private final MongoCollection<Document> collection;

    public FenceStore(String uri) {
        ConnectionString cs = new ConnectionString(uri);
        if (cs.getDatabase() == null) {
            throw new IllegalArgumentException("Mongo 连接串必须指定库名，如 mongodb://host:27017/bike_monitor");
        }
        this.client = MongoClients.create(cs);
        MongoDatabase db = client.getDatabase(cs.getDatabase());
        this.collection = db.getCollection(COLLECTION);
    }

    @SuppressWarnings("unchecked")
    public List<FenceSite> loadAll() {
        List<FenceSite> sites = new ArrayList<>();
        for (Document doc : collection.find()) {
            Document loc = doc.get("location", Document.class);
            if (loc == null) {
                continue;
            }
            List<List<List<Double>>> coords =
                    (List<List<List<Double>>>) (Object) loc.get("coordinates", List.class);
            if (coords == null || coords.isEmpty() || coords.get(0).size() < 4) {
                continue;
            }
            List<List<Double>> ring = coords.get(0);
            double[][] vertices = new double[ring.size()][2];
            for (int i = 0; i < ring.size(); i++) {
                vertices[i][0] = ring.get(i).get(0);
                vertices[i][1] = ring.get(i).get(1);
            }
            String name = doc.getString("name");
            sites.add(new FenceSite(doc.getLong("fenceId"), name, vertices, parseCapacity(name)));
        }
        return sites;
    }

    /** [停放24] xxx → 24；[禁停]、无前缀、[停放0] → 0 / null（不参与分配） */
    static Integer parseCapacity(String name) {
        if (name == null) {
            return null;
        }
        Matcher m = CAPACITY.matcher(name);
        if (!m.find()) {
            return null;
        }
        return Integer.parseInt(m.group(1));
    }

    @Override
    public void close() {
        client.close();
    }
}
