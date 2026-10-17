package com.bike.monitor.common.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordConverterTest {

    private static final double LNG = 116.4074;
    private static final double LAT = 39.9042;

    @Test
    void should_offset_300_to_600m_when_wgs84_to_gcj02_in_beijing() {
        double[] gcj = CoordConverter.wgs84ToGcj02(LNG, LAT);
        double dist = GeoUtil.haversine(LNG, LAT, gcj[0], gcj[1]);
        assertTrue(dist > 300 && dist < 600, "offset=" + dist);
    }

    @Test
    void should_no_offset_when_point_out_of_china() {
        double[] gcj = CoordConverter.wgs84ToGcj02(-74.006, 40.7128);
        assertEquals(-74.006, gcj[0], 1e-9);
        assertEquals(40.7128, gcj[1], 1e-9);
    }

    @Test
    void should_roundtrip_within_1e_5_deg_when_gcj02_to_wgs84() {
        double[] gcj = CoordConverter.wgs84ToGcj02(LNG, LAT);
        double[] wgs = CoordConverter.gcj02ToWgs84(gcj[0], gcj[1]);
        assertEquals(LNG, wgs[0], 1e-5);
        assertEquals(LAT, wgs[1], 1e-5);
    }

    @Test
    void should_roundtrip_when_wgs84_to_bd09() {
        double[] bd = CoordConverter.wgs84ToBd09(LNG, LAT);
        double[] wgs = CoordConverter.bd09ToWgs84(bd[0], bd[1]);
        assertEquals(LNG, wgs[0], 1e-5);
        assertEquals(LAT, wgs[1], 1e-5);
    }

    @Test
    void should_bd09_offset_larger_than_gcj02() {
        double[] gcj = CoordConverter.wgs84ToGcj02(LNG, LAT);
        double[] bd = CoordConverter.wgs84ToBd09(LNG, LAT);
        double d1 = GeoUtil.haversine(LNG, LAT, gcj[0], gcj[1]);
        double d2 = GeoUtil.haversine(LNG, LAT, bd[0], bd[1]);
        assertTrue(d2 > d1, "BD09 偏移应大于 GCJ02");
    }
}
