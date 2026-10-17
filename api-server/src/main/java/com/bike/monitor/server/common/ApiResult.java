package com.bike.monitor.server.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一响应体 {code, msg, data}。
 */
@Data
@AllArgsConstructor
public class ApiResult<T> {

    public static final int CODE_OK = 0;
    public static final int CODE_ERR = 1;

    private int code;
    private String msg;
    private T data;

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(CODE_OK, "ok", data);
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(CODE_OK, "ok", null);
    }

    public static <T> ApiResult<T> error(String msg) {
        return new ApiResult<>(CODE_ERR, msg, null);
    }
}
