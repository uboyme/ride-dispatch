package com.caojie.ridedispatch.common;

public record Result<T>(int code, String message, T data) {
    // 成功，带数据
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    // 成功，不带数据（比如上线、下线这类操作）
    public static Result<Void> ok() {
        return new Result<>(0, "success", null);
    }

    // 失败
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
