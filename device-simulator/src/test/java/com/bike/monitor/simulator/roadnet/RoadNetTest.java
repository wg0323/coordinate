package com.bike.monitor.simulator.roadnet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoadNetTest {

    private RoadNet net() throws Exception {
        return RoadNet.loadFromClasspath("roadnet/test-grid.json");
    }

    @Test
    void should_load_nodes_and_edges() throws Exception {
        assertEquals(9, net().nodeCount());
    }

    @Test
    void should_find_nearest_node() throws Exception {
        assertEquals(0, net().nearestNode(116.4001, 39.9001));
        assertEquals(8, net().nearestNode(116.4199, 39.9199));
    }

    @Test
    void should_find_shortest_path() throws Exception {
        RoadNet net = net();
        RoadNet.Path path = net.shortestPath(0, 8);
        assertNotNull(path);
        assertEquals(0, path.nodes[0]);
        assertEquals(8, path.nodes[path.nodes.length - 1]);
        // 0→3→4→5→8：2×1112 + 2×853
        assertEquals(2 * 1112 + 2 * 853, path.lengthM, 1.0);
    }

    @Test
    void should_interpolate_point_on_first_edge() throws Exception {
        RoadNet net = net();
        RoadNet.Path path = net.shortestPath(0, 2);
        double[] p = net.pointAt(path, 0);
        assertEquals(116.400, p[0], 1e-6);
        assertEquals(39.900, p[1], 1e-6);
        // 走到第二条边中点附近，纬度仍为 39.900，经度在 116.410 与 116.420 之间
        double[] mid = net.pointAt(path, 853 + 426);
        assertEquals(39.900, mid[1], 1e-4);
        assertTrue(mid[0] > 116.414 && mid[0] < 116.416);
    }

    @Test
    void should_measure_distance_to_nearest_edge() throws Exception {
        RoadNet net = net();
        assertEquals(0, net.nearestEdgeDistanceM(116.405, 39.900), 1.0);
        assertTrue(net.nearestEdgeDistanceM(116.405, 39.905) > 400);
    }

    @Test
    void should_return_null_when_unreachable() throws Exception {
        RoadNet net = net();
        // 手工构造断开的图：仅 0-1 一条边
        RoadNet isolated = new RoadNet(
                new double[][]{{116.4, 39.9}, {116.41, 39.9}, {116.5, 39.95}},
                new int[][]{{0, 1, 853}});
        assertNull(isolated.shortestPath(0, 2));
        assertNotNull(net.shortestPath(0, 1));
    }
}
