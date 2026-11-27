package com.caojie.ridedispatch.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 1. 业务异常：我们自己主动抛出的
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        log.warn("业务异常 code={} message={}", e.getErrorCode().getCode(), e.getMessage());
        return build(e.getErrorCode(), e.getMessage());
    }

    // 2. @Valid 校验不通过
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null
                ? ErrorCode.PARAM_INVALID.getMessage()
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        log.warn("参数校验失败 {}", message);
        return build(ErrorCode.PARAM_INVALID, message);
    }

    // 3. 请求体读不了：JSON 格式写错，或者枚举传了不存在的值
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误 {}", e.getMessage());
        return build(ErrorCode.PARAM_INVALID, "请求体格式错误");
    }

    // 4. 缺少必填的请求参数，比如漏了 ?driverId=
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return build(ErrorCode.PARAM_INVALID, "缺少参数 " + e.getParameterName());
    }

    // 5. 参数类型不对，比如 /orders/abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(ErrorCode.PARAM_INVALID, "参数 " + e.getName() + " 格式错误");
    }

    // 6. 请求方法用错，比如该用 POST 却用了 GET
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    // 7.请求体类型不对，比如没有用 JSON 格式
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage());
    }

    // 8. 路径不存在
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return build(ErrorCode.ROUTE_NOT_FOUND, ErrorCode.ROUTE_NOT_FOUND.getMessage());
    }

    // 9. 兜底：其他所有意料之外的错误
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("系统异常", e);
        return build(ErrorCode.SYSTEM_ERROR, ErrorCode.SYSTEM_ERROR.getMessage());
    }

    // 统一组装响应：HTTP 状态码 + Result
    private ResponseEntity<Result<Void>> build(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.fail(errorCode.getCode(), message));
    }
}
