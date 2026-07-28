package com.limitedgoods.limitedgoods.common.response;

public record FieldErrorResponse(
        String field,
        String message
) {
}