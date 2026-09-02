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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e) {
    ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
      errors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    logByLevel(errorCode, e);
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.fail(errorCode, errors));
  }

  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleBindException(BindException e) {
    ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
      errors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    logByLevel(errorCode, e);
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.fail(errorCode, errors));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
      ConstraintViolationException e) {
    ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
    logByLevel(errorCode, e);
    return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException e) {
    ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
    logByLevel(errorCode, e);
    return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
    ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
    logByLevel(errorCode, e);
    return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
  }

  /**multipart 업로드 용량 초과 처리.
   *
   * max-file-size / max-request-size 초과는 Spring이 multipart를 파싱하는 시점에 예외를 던지기 때문에 컨트롤러와 서비스의 도메인 검증
   * (NL4003 장당 10MB / NL4008 총합 50MB)이 아예 실행되지 않는다. 핸들러가 없으면 500이 나가므로 413으로 명확히 내려준다.
   *
   * application.yml의 한계값을 도메인 검증값보다 약간 크게 잡아두었기 때문에, 정상 범위를 조금 넘긴 요청은 NL4003/NL4008로 안내되고 이 핸들러는
   * 그보다 훨씬 큰 요청을 막는 최종 방어선 역할을 한다.
   */
   @ExceptionHandler(MaxUploadSizeExceededException.class)
   public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
       MaxUploadSizeExceededException e) {
       ErrorCode errorCode = ErrorCode.FILE_UPLOAD_SIZE_EXCEEDED;
       logByLevel(errorCode, e);
       return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
   }

   /** multipart 파트 누락 처리.
    *
    * 가정통신문 업로드의 파트명이 file → files로 변경되었기 때문에, 클라이언트가 예전 파트명으로 보내면 MissingServletRequestPartException이
    * 발생한다. 핸들러가 없으면 원인을 알 수 없는 500이 나가므로 400으로 명확히 내려준다.
    */
   @ExceptionHandler(MissingServletRequestPartException.class)
   public ResponseEntity<ApiResponse<Void>> handleMissingRequestPart(
       MissingServletRequestPartException e) {
       ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
       logByLevel(errorCode, e);
       return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
   }

   /** 필수 쿼리 파라미터 누락도 동일하게 400으로 처리한다. (기존에는 500으로 나갔음) */
   @ExceptionHandler(MissingServletRequestParameterException.class)
   public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(
       MissingServletRequestParameterException e) {
       ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
       logByLevel(errorCode, e);
       return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
   }

   /**
    * 파라미터 타입 불일치 처리. (예: childId에 숫자가 아닌 값 전달)
    *
    * MethodArgumentTypeMismatchException은 BindException 계열이 아니라서 기존에는 마지막 Exception 핸들러로 떨어져 500이
    * 나갔다. 클라이언트 입력 오류이므로 400으로 내려준다.
    */
   @ExceptionHandler(MethodArgumentTypeMismatchException.class)
   public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
       MethodArgumentTypeMismatchException e) {
       ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
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
