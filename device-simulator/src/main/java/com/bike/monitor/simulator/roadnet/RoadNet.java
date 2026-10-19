package com.bike.monitor.simulator.roadnet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 固化路网：节点 + 无向边。A* 用于生成沿道路的骑行路径。
 */
public class RoadNet {

    private static final double METERS_PER_DEG_LAT = 111320.0;

    public static class Path {
        public final int[] nodes;
        public final double lengthM;

        Path(int[] nodes, double lengthM) {
            this.nodes = nodes;
            this.lengthM = lengthM;
        }
    }

    private static class Edge {
        final int to;
        final double lengthM;

        Edge(int to, double lengthM) {
            this.to = to;
            this.lengthM = lengthM;
        }
    }

    private static class State {
        final int idx;
        final double g;
        final double f;

        State(int idx, double g, double f) {
            this.idx = idx;
            this.g = g;
            this.f = f;
        }
    }

    private final double[][] nodes;
    private final List<Edge>[] adj;

    @SuppressWarnings("unchecked")
    public RoadNet(double[][] nodes, int[][] edges) {
        this.nodes = nodes;
        this.adj = new List[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            adj[i] = new ArrayList<>();
        }
        for (int[] e : edges) {
            double len = e[2] > 0 ? e[2] : haversine(nodes[e[0]][0], nodes[e[0]][1],
                    nodes[e[1]][0], nodes[e[1]][1]);
            adj[e[0]].add(new Edge(e[1], len));
            adj[e[1]].add(new Edge(e[0], len));
        }
    }

    public static RoadNet loadFromClasspath(String resource) throws IOException {
        try (InputStream in = RoadNet.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("路网资源不存在: " + resource + "（先运行 scripts/fetch-roadnet.mjs）");
            }
            JsonNode root = new ObjectMapper().readTree(in);
            JsonNode nodeArr = root.get("nodes");
            JsonNode edgeArr = root.get("edges");
            double[][] nodes = new double[nodeArr.size()][2];
            for (int i = 0; i < nodeArr.size(); i++) {
                nodes[i][0] = nodeArr.get(i).get(0).asDouble();
                nodes[i][1] = nodeArr.get(i).get(1).asDouble();
            }
            int[][] edges = new int[edgeArr.size()][3];
            for (int i = 0; i < edgeArr.size(); i++) {
                edges[i][0] = edgeArr.get(i).get(0).asInt();
                edges[i][1] = edgeArr.get(i).get(1).asInt();
                edges[i][2] = (int) Math.round(edgeArr.get(i).get(2).asDouble());
            }
            return new RoadNet(nodes, edges);
        }
    }

    public int nodeCount() {
        return nodes.length;
    }

    public int nearestNode(double lng, double lat) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < nodes.length; i++) {
            double d = haversine(lng, lat, nodes[i][0], nodes[i][1]);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public Path shortestPath(int from, int to) {
        if (from == to) {
            return new Path(new int[]{from}, 0);
        }
        int n = nodes.length;
        double[] g = new double[n];
        Arrays.fill(g, Double.POSITIVE_INFINITY);
        int[] prev = new int[n];
        Arrays.fill(prev, -1);
        boolean[] closed = new boolean[n];
        PriorityQueue<State> open = new PriorityQueue<>(Comparator.comparingDouble(s -> s.f));
        g[from] = 0;
        open.add(new State(from, 0, heuristic(from, to)));
        while (!open.isEmpty()) {
            State cur = open.poll();
            if (closed[cur.idx]) {
                continue;
            }
            if (cur.idx == to) {
                break;
            }
            closed[cur.idx] = true;
            for (Edge e : adj[cur.idx]) {
                if (closed[e.to]) {
                    continue;
                }
                double ng = g[cur.idx] + e.lengthM;
                if (ng < g[e.to]) {
                    g[e.to] = ng;
                    prev[e.to] = cur.idx;
                    open.add(new State(e.to, ng, ng + heuristic(e.to, to)));
                }
            }
        }
        if (prev[to] == -1) {
            return null;
        }
        int count = 0;
        for (int cur = to; cur != -1; cur = prev[cur]) {
            count++;
        }
        int[] path = new int[count];
        int cur = to;
        for (int i = count - 1; i >= 0; i--) {
            path[i] = cur;
            cur = prev[cur];
        }
        return new Path(path, g[to]);
    }

    /** 沿路径按距离插值，返回 [lng, lat, bearingDeg] */
    public double[] pointAt(Path path, double distanceM) {
        if (distanceM <= 0 || path.nodes.length == 1) {
            if (path.nodes.length == 1) {
                return new double[]{nodes[path.nodes[0]][0], nodes[path.nodes[0]][1], 0};
            }
            distanceM = 0;
        }
        double remain = distanceM;
        for (int i = 0; i + 1 < path.nodes.length; i++) {
            double[] a = nodes[path.nodes[i]];
            double[] b = nodes[path.nodes[i + 1]];
            double len = haversine(a[0], a[1], b[0], b[1]);
            if (remain <= len || i == path.nodes.length - 2) {
                double t = len == 0 ? 0 : Math.min(1, remain / len);
                double lng = a[0] + (b[0] - a[0]) * t;
                double lat = a[1] + (b[1] - a[1]) * t;
                double dLng = (b[0] - a[0]) * Math.cos(Math.toRadians(lat)) * METERS_PER_DEG_LAT;
                double dLat = (b[1] - a[1]) * METERS_PER_DEG_LAT;
                double bearing = (Math.toDegrees(Math.atan2(dLng, dLat)) + 360) % 360;
                return new double[]{lng, lat, bearing};
            }
            remain -= len;
        }
        double[] last = nodes[path.nodes[path.nodes.length - 1]];
        return new double[]{last[0], last[1], 0};
    }

    private double heuristic(int a, int b) {
        return haversine(nodes[a][0], nodes[a][1], nodes[b][0], nodes[b][1]);
    }

    /** 点到最近路网边的距离（米），用于验证生成点是否落在道路上 */
    public double nearestEdgeDistanceM(double lng, double lat) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < nodes.length; i++) {
            for (Edge e : adj[i]) {
                if (e.to < i) {
                    continue; // 每条无向边只算一次
                }
                best = Math.min(best, pointToSegmentM(lng, lat, nodes[i], nodes[e.to]));
            }
        }
        return best;
    }

    private static double pointToSegmentM(double lng, double lat, double[] a, double[] b) {
        double kx = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(lat));
        double ky = METERS_PER_DEG_LAT;
        double ax = a[0] * kx;
        double ay = a[1] * ky;
        double bx = b[0] * kx;
        double by = b[1] * ky;
        double px = lng * kx;
        double py = lat * ky;
        double dx = bx - ax;
        double dy = by - ay;
        double norm = dx * dx + dy * dy;
        double t = norm == 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / norm;
        t = Math.max(0, Math.min(1, t));
        double qx = ax + dx * t;
        double qy = ay + dy * t;
        return Math.hypot(px - qx, py - qy);
    }

    public static double haversine(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6371000 * Math.asin(Math.sqrt(a));
    }
}
