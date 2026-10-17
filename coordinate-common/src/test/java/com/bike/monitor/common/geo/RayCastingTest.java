package com.bike.monitor.common.geo;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RayCastingTest {

    /** L 形凹多边形 */
    private static final double[][] L_SHAPE = {
            {0, 0}, {4, 0}, {4, 2}, {2, 2}, {2, 4}, {0, 4}
    };

    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void should_true_when_point_inside() {
        assertTrue(RayCasting.contains(L_SHAPE, 1, 1));
        assertTrue(RayCasting.contains(L_SHAPE, 3, 1));
        assertTrue(RayCasting.contains(L_SHAPE, 1, 3));
    }

    @Test
    void should_false_when_point_in_concave_notch() {
        assertFalse(RayCasting.contains(L_SHAPE, 3, 3));
    }

    @Test
    void should_false_when_point_outside() {
        assertFalse(RayCasting.contains(L_SHAPE, 5, 5));
        assertFalse(RayCasting.contains(L_SHAPE, -1, 1));
    }

    @Test
    void should_true_when_point_on_boundary_or_vertex() {
        assertTrue(RayCasting.contains(L_SHAPE, 0, 0));
        assertTrue(RayCasting.contains(L_SHAPE, 2, 2));
        assertTrue(RayCasting.contains(L_SHAPE, 2, 3));
        assertTrue(RayCasting.contains(L_SHAPE, 4, 1));
    }

    @Test
    void should_match_jts_contains_for_sample_points() {
        // JTS LinearRing 要求首尾闭合，补上首点
        java.util.List<Coordinate> coords = new java.util.ArrayList<>();
        for (double[] p : L_SHAPE) {
            coords.add(new Coordinate(p[0], p[1]));
        }
        coords.add(coords.get(0));
        Polygon jts = FACTORY.createPolygon(coords.toArray(new Coordinate[0]));
        double[][] samples = {
                {1, 1}, {3, 1}, {1, 3}, {3, 3}, {5, 5}, {-1, 1},
                {2, 1}, {4, 1}, {0, 0}, {2, 2}, {2, 3}, {3, 2}, {0.5, 3.5}, {3.9, 1.9}
        };
        for (double[] p : samples) {
            // 本实现边界/顶点视为在内，语义对应 JTS covers（contains 不含边界）
            boolean jtsIn = jts.covers(FACTORY.createPoint(new Coordinate(p[0], p[1])));
            assertEquals(jtsIn, RayCasting.contains(L_SHAPE, p[0], p[1]),
                    "point(" + p[0] + "," + p[1] + ") jts=" + jtsIn);
        }
    }
}
