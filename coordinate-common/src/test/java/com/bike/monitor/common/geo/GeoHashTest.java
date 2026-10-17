package com.bike.monitor.common.geo;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoHashTest {

    @Test
    void should_encode_wiki_reference_when_precision_5() {
        // Wikipedia GeoHash 词条标准示例：(42.605, -5.603) 精度 5 位 = ezs42
        assertEquals("ezs42", GeoHash.encode(-5.603, 42.605, 5));
    }

    @Test
    void should_start_with_wx_when_beijing() {
        String h = GeoHash.encode(116.4074, 39.9042, 5);
        assertTrue(h.startsWith("wx"), h);
    }

    @Test
    void should_prefix_consistent_when_different_precision() {
        assertEquals(GeoHash.encode(116.4074, 39.9042, 12).substring(0, 5),
                GeoHash.encode(116.4074, 39.9042, 5));
    }

    @Test
    void should_decode_back_within_0_001deg() {
        String h = GeoHash.encode(116.4074, 39.9042, 7);
        double[] c = GeoHash.decode(h);
        assertEquals(116.4074, c[0], 0.001);
        assertEquals(39.9042, c[1], 0.001);
    }

    @Test
    void should_return_self_plus_8_neighbors_when_around() {
        List<String> around = GeoHash.around(GeoHash.encode(116.4074, 39.9042, 5));
        assertEquals(9, around.size());
        assertEquals(9, new HashSet<>(around).size());
    }

    @Test
    void should_neighbors_cover_adjacent_cell_points() {
        String h = GeoHash.encode(116.4074, 39.9042, 5);
        double[] bbox = GeoHash.decodeBbox(h);
        double cellLng = bbox[1] - bbox[0];
        double cellLat = bbox[3] - bbox[2];
        Set<String> around = new HashSet<>(GeoHash.around(h));
        // 向东/向北移动 1.5 个格后的点，其 geohash 必须落在九宫格内
        assertTrue(around.contains(GeoHash.encode(bbox[0] + cellLng * 1.5, (bbox[2] + bbox[3]) / 2, 5)));
        assertTrue(around.contains(GeoHash.encode((bbox[0] + bbox[1]) / 2, bbox[2] + cellLat * 1.5, 5)));
    }
}
