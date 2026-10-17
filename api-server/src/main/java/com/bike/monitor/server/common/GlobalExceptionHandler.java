package com.bike.monitor.server.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：所有未捕获异常统一转 ApiResult，前端不再收到 Spring 默认错误体。
 * 业务异常消息原样返回；其余异常记录完整堆栈后只给通用提示，不暴露内部细节。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：消息原样返回 */
    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ApiResult.error(e.getMessage());
    }

    /** 兜底：完整堆栈进日志，前端只给通用提示 */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ApiResult.error("系统繁忙，请稍后重试");
    }
}
