package com.caojie.ridedispatch.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    SUCCESS(0, HttpStatus.OK, "success"),

    PARAM_INVALID(40000, HttpStatus.BAD_REQUEST, "参数错误"),
    ROUTE_NOT_FOUND(40400, HttpStatus.NOT_FOUND, "接口不存在"),
    DRIVER_NOT_FOUND(40401, HttpStatus.NOT_FOUND, "司机不存在"),
    ORDER_NOT_FOUND(40402, HttpStatus.NOT_FOUND, "订单不存在"),
    METHOD_NOT_ALLOWED(40500, HttpStatus.METHOD_NOT_ALLOWED, "不支持的请求方法"),
    DRIVER_STATUS_CONFLICT(40901, HttpStatus.CONFLICT, "司机当前状态不允许该操作"),
    ORDER_STATUS_CONFLICT(40902, HttpStatus.CONFLICT, "订单当前状态不允许该操作"),
    ORDER_CHANGED(40903, HttpStatus.CONFLICT, "订单状态已变化，请刷新后重试"),
    UNSUPPORTED_MEDIA_TYPE(41500, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求体必须是 JSON 格式"),

    SYSTEM_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试");

    private final int code;              // 业务错误码，返回给前端
    private final HttpStatus httpStatus; // HTTP 状态码
    private final String message;        // 默认提示
}
