package com.gachi.be.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON4001", "입력값이 올바르지 않습니다.", "요청 DTO/파라미터 검증 실패", ErrorLogLevel.WARN),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON4002", "지원하지 않는 HTTP 메서드입니다.", "잘못된 메서드 요청", ErrorLogLevel.WARN),
    BUSINESS_RULE_VIOLATION(HttpStatus.BAD_REQUEST, "BUS4001", "비즈니스 규칙 위반입니다.", "도메인 정책 위반", ErrorLogLevel.WARN),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER4041", "사용자를 찾을 수 없습니다.", "식별자에 해당하는 사용자 없음", ErrorLogLevel.INFO),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "EXT5021", "외부 API 호출 중 오류가 발생했습니다.", "외부 연동 실패/타임아웃", ErrorLogLevel.ERROR),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON5001", "서버 내부 오류가 발생했습니다.", "처리되지 않은 예외", ErrorLogLevel.ERROR);

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
    private final String description;
    private final ErrorLogLevel logLevel;
}
