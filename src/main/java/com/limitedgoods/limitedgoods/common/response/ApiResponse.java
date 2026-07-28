package com.limitedgoods.limitedgoods.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true,
                "SUCCESS",
                "요청 성공",
                data
        );
    }

    public static <T> ApiResponse<T> success(
            String message,
            T data
    ) {
        return new ApiResponse<>(
                true,
                "SUCCESS",
                message,
                data
        );
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(
                true,
                "SUCCESS",
                "요청성공",
                null
        );
    }

    public static ApiResponse<Void> fail(
            String code,
            String message
    ) {
        return new ApiResponse<>(
                false,
                code,
                message,
                null
        );
    }

    public static <T> ApiResponse<T> fail(
            String code,
            String message,
            T data
    ) {
        return new ApiResponse<>(
                false,
                code,
                message,
                data
        );
    }
}
