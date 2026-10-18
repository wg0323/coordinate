package com.bike.monitor.server.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 围栏名称容量前缀解析：原始数据将容量限制编码进名称前缀（"[停放24] 国贸站B口"）。
 * [禁停] 与无前缀围栏无容量语义。
 */
public final class FenceCapacityParser {

    private static final Pattern PATTERN = Pattern.compile("^\\[停放(\\d+)]");

    private FenceCapacityParser() {
    }

    /** 解析容量；无 [停放N] 前缀或格式非法返回 empty */
    public static Optional<Integer> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        Matcher m = PATTERN.matcher(name);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(m.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
