package com.bike.monitor.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 车辆状态 hash 值编解码测试（bike:status，value = "locked,district,ts"）。
 */
class BikeStatusCodecTest {

    @Test
    void should_decode_all_fields_when_value_is_well_formed() {
        BikeStatusCodec.StatusEntry e = BikeStatusCodec.decode("0,中心城区,1730000000000");
        assertFalse(e.isLocked());
        assertEquals("中心城区", e.getDistrict());
        assertEquals(1730000000000L, e.getTs());
    }

    @Test
    void should_decode_locked_true_when_locked_flag_is_one() {
        BikeStatusCodec.StatusEntry e = BikeStatusCodec.decode("1,通州,123");
        assertTrue(e.isLocked());
    }

    @Test
    void should_return_null_when_value_is_malformed() {
        assertNull(BikeStatusCodec.decode(null));
        assertNull(BikeStatusCodec.decode("garbage"));
        assertNull(BikeStatusCodec.decode("1,区,notANumber"));
        assertNull(BikeStatusCodec.decode("only,three,fields,extra"));
    }
}
