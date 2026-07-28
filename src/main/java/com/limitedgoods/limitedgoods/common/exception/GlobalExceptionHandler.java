package com.limitedgoods.limitedgoods.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.limitedgoods.limitedgoods.common.response.ApiResponse;
import com.limitedgoods.limitedgoods.common.response.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException e,
            HttpServletRequest request) {

        ErrorCode errorCode = e.getErrorCode();

        log.warn(
                "event=business_exception component=application " +
                        "method={} path={} errorCode={} message={}",
                request.getMethod(),
                request.getRequestURI(),
                errorCode.getCode(),
                e.getMessage()
        );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(
                        ApiResponse.fail(
                                errorCode.getCode(),
                                e.getMessage()
                        )
                );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        List<FieldErrorResponse> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldErrorResponse(
                        error.getField(),
                        Objects.requireNonNullElse(
                                error.getDefaultMessage(),
                                "올바르지 않은 값입니다."
                        )
                ))
                .toList();

        log.warn(
                "event=request_validation_failed method={} path={} errors={}",
                request.getMethod(),
                request.getRequestURI(),
                errors
        );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        errors
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleConstraintViolation(
            ConstraintViolationException e,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        List<FieldErrorResponse> errors = e.getConstraintViolations()
                .stream()
                .map(violation -> new FieldErrorResponse(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        log.warn(
                "event=constraint_violation method={} path={} errors={}",
                request.getMethod(),
                request.getRequestURI(),
                errors
        );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        errors
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_JSON;

        Throwable cause = e.getCause();

        if (cause instanceof InvalidFormatException invalidFormatException) {
            String field = invalidFormatException.getPath()
                    .stream()
                    .map(reference -> reference.getFieldName())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));

            List<FieldErrorResponse> errors = List.of(
                    new FieldErrorResponse(
                            field.isBlank() ? "requestBody" : field,
                            "허용되지 않은 값 또는 형식입니다."
                    )
            );

            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.fail(
                            ErrorCode.TYPE_MISMATCH.getCode(),
                            ErrorCode.TYPE_MISMATCH.getMessage(),
                            errors
                    ));
        }

        log.warn(
                "event=invalid_json method={} path={} message={}",
                request.getMethod(),
                request.getRequestURI(),
                e.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.fail(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleMissingRequestHeader(
            MissingRequestHeaderException e
    ) {
        ErrorCode errorCode = ErrorCode.MISSING_REQUIRED_VALUE;

        List<FieldErrorResponse> errors = List.of(
                new FieldErrorResponse(
                        e.getHeaderName(),
                        "필수 헤더가 누락되었습니다."
                )
        );

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.fail(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        errors
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleMissingRequestParameter(
            MissingServletRequestParameterException e
    ) {
        ErrorCode errorCode = ErrorCode.MISSING_REQUIRED_VALUE;

        List<FieldErrorResponse> errors = List.of(
                new FieldErrorResponse(
                        e.getParameterName(),
                        "필수 파라미터가 누락되었습니다."
                )
        );

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.fail(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        errors
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e
    ) {
        ErrorCode errorCode = ErrorCode.TYPE_MISMATCH;

        List<FieldErrorResponse> errors = List.of(
                new FieldErrorResponse(
                        e.getName(),
                        "요청 값의 타입 또는 형식이 올바르지 않습니다."
                )
        );

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.fail(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        errors
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException e
    ) {
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception e,
            HttpServletRequest request
    ) {
        log.error(
                "event=unexpected_exception component=application method={} path={}",
                request.getMethod(),
                request.getRequestURI(),
                e
        );

        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail(
                        "INTERNAL_SERVER_ERROR",
                        "서버 오류가 발생했습니다."
                ));
    }
}