package com.lawai.legalassistant.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 统一响应体
 *
 * @param code      业务码，0 表示成功
 * @param message   提示信息
 * @param data      业务数据
 * @param timestamp 时间戳
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(
        int code,
        String message,
        T data,
        Instant timestamp
) {

    private static final int SUCCESS_CODE = 0;
    private static final String SUCCESS_MSG = "success";

    public static <T> Result<T> success() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MSG, null, Instant.now());
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MSG, data, Instant.now());
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(SUCCESS_CODE, message, data, Instant.now());
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, Instant.now());
    }
}
