package com.bike.monitor.simulator.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimConfigTest {

    @Test
    void should_parse_history_trace_options() {
        SimConfig c = SimConfig.parse(new String[]{
                "--mode=history-trace", "--trace-bike=BMXC000123",
                "--trace-day-offset=1", "--trace-segments=5", "--trace-seed=42"
        });
        assertEquals("history-trace", c.getMode());
        assertEquals("BMXC000123", c.getTraceBike());
        assertEquals("中心城区", c.getTraceDistrict());
        assertEquals(1, c.getTraceDayOffset());
        assertEquals(5, c.getTraceSegments());
        assertEquals(42L, c.getTraceSeed());
    }

    @Test
    void should_keep_defaults_when_not_specified() {
        SimConfig c = SimConfig.parse(new String[]{});
        assertEquals("", c.getMode());
        assertEquals("BMXC000123", c.getTraceBike());
    }
}
