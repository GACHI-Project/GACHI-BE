package com.gachi.be.global.exception;

import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.code.ErrorLogLevel;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        logByLevel(errorCode, e);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode, errors));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBindException(BindException e) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        logByLevel(errorCode, e);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode, errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        logByLevel(errorCode, e);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        logByLevel(errorCode, e);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException e) {
        ErrorCode errorCode = e.getErrorCode();
        logByLevel(errorCode, e);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception e) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        logByLevel(errorCode, e);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
    }

    private void logByLevel(ErrorCode errorCode, Exception e) {
        if (errorCode.getLogLevel() == ErrorLogLevel.INFO) {
            log.info("[{}] {}", errorCode.getCode(), e.getMessage());
            return;
        }
        if (errorCode.getLogLevel() == ErrorLogLevel.WARN) {
            log.warn("[{}] {}", errorCode.getCode(), e.getMessage());
            return;
        }
        log.error("[{}] {}", errorCode.getCode(), e.getMessage(), e);
    }
}
