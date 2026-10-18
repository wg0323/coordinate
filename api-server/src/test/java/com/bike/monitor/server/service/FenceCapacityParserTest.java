package com.bike.monitor.server.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 围栏名称容量前缀解析测试："[停放24] 国贸站B口" → 24。
 */
class FenceCapacityParserTest {

    @Test
    void should_parse_capacity_from_parking_prefix() {
        assertEquals(Optional.of(24), FenceCapacityParser.parse("[停放24] 国贸站B口"));
    }

    @Test
    void should_keep_zero_capacity() {
        assertEquals(Optional.of(0), FenceCapacityParser.parse("[停放0] 某处"));
    }

    @Test
    void should_return_empty_for_no_parking_prefix() {
        assertFalse(FenceCapacityParser.parse("[禁停] 灵境胡同").isPresent());
        assertFalse(FenceCapacityParser.parse("国贸站B口").isPresent());
    }

    @Test
    void should_return_empty_for_malformed_prefix() {
        assertFalse(FenceCapacityParser.parse("[停放] 无数字").isPresent());
        assertFalse(FenceCapacityParser.parse("[停放abc] 非数字").isPresent());
        assertFalse(FenceCapacityParser.parse(null).isPresent());
    }
}
