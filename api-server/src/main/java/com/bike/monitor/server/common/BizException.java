package com.bike.monitor.server.common;

/**
 * 业务异常：message 面向调用方，可原样返回给前端（不得携带堆栈等系统内部细节）。
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
