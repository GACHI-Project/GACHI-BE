package com.gachi.be.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  INVALID_INPUT_VALUE(
      HttpStatus.BAD_REQUEST,
      "COMMON4001",
      "입력값이 올바르지 않습니다.",
      "요청 DTO/파라미터 검증 실패",
      ErrorLogLevel.WARN),
  METHOD_NOT_ALLOWED(
      HttpStatus.METHOD_NOT_ALLOWED,
      "COMMON4002",
      "지원하지 않는 HTTP 메서드입니다.",
      "잘못된 메서드 요청",
      ErrorLogLevel.WARN),
  BUSINESS_RULE_VIOLATION(
      HttpStatus.BAD_REQUEST, "BUS4001", "비즈니스 규칙 위반입니다.", "도메인 정책 위반", ErrorLogLevel.WARN),
  RESOURCE_NOT_FOUND(
      HttpStatus.NOT_FOUND,
      "COMMON4041",
      "요청한 리소스를 찾을 수 없습니다.",
      "요청 경로에 해당하는 리소스 없음",
      ErrorLogLevel.INFO),
  USER_NOT_FOUND(
      HttpStatus.NOT_FOUND, "USER4041", "사용자를 찾을 수 없습니다.", "식별자에 해당하는 사용자 없음", ErrorLogLevel.INFO),
  AUTH_DUPLICATE_EMAIL(
      HttpStatus.CONFLICT, "AUTH4091", "이미 사용 중인 이메일입니다.", "회원가입 이메일 중복", ErrorLogLevel.WARN),
  AUTH_DUPLICATE_LOGIN_ID(
      HttpStatus.CONFLICT, "AUTH4092", "이미 사용 중인 아이디입니다.", "회원가입 login_id 중복", ErrorLogLevel.WARN),
  AUTH_DUPLICATE_PHONE_NUMBER(
      HttpStatus.CONFLICT, "AUTH4093", "이미 사용 중인 전화번호입니다.", "회원가입 전화번호 중복", ErrorLogLevel.WARN),
  AUTH_EMAIL_NOT_VERIFIED(
      HttpStatus.BAD_REQUEST,
      "AUTH4001",
      "이메일 인증이 완료되지 않았습니다.",
      "회원가입 전 이메일 인증 필요",
      ErrorLogLevel.WARN),
  AUTH_EMAIL_SEND_COOLDOWN(
      HttpStatus.TOO_MANY_REQUESTS,
      "AUTH4291",
      "인증 코드 재발송 대기 시간입니다.",
      "재발송 쿨타임 위반",
      ErrorLogLevel.WARN),
  AUTH_EMAIL_CODE_MISMATCH(
      HttpStatus.BAD_REQUEST, "AUTH4002", "인증 코드가 일치하지 않습니다.", "인증 코드 불일치", ErrorLogLevel.WARN),
  AUTH_EMAIL_CODE_EXPIRED(
      HttpStatus.BAD_REQUEST, "AUTH4003", "인증 코드가 만료되었습니다.", "인증 코드 TTL 만료", ErrorLogLevel.WARN),
  AUTH_EMAIL_CODE_ATTEMPT_EXCEEDED(
      HttpStatus.TOO_MANY_REQUESTS,
      "AUTH4292",
      "인증 코드 입력 시도 횟수를 초과했습니다.",
      "인증 코드 최대 시도 초과",
      ErrorLogLevel.WARN),
  AUTH_INVALID_CREDENTIALS(
      HttpStatus.UNAUTHORIZED,
      "AUTH4011",
      "아이디 또는 비밀번호가 올바르지 않습니다.",
      "로그인 인증 실패",
      ErrorLogLevel.WARN),
  AUTH_ACCOUNT_WITHDRAWN(
      HttpStatus.FORBIDDEN, "AUTH4031", "탈퇴한 계정입니다.", "탈퇴 계정 로그인/토큰 발급 차단", ErrorLogLevel.WARN),
  AUTH_PASSWORD_CONFIRM_MISMATCH(
      HttpStatus.BAD_REQUEST,
      "AUTH4004",
      "비밀번호 확인 값이 일치하지 않습니다.",
      "회원가입 비밀번호 확인 불일치",
      ErrorLogLevel.WARN),
  AUTH_CONSENT_REQUIRED(
      HttpStatus.BAD_REQUEST,
      "AUTH4005",
      "약관 및 개인정보 처리방침 동의가 필요합니다.",
      "회원가입 동의 미체크",
      ErrorLogLevel.WARN),
  AUTH_REFRESH_TOKEN_INVALID(
      HttpStatus.UNAUTHORIZED,
      "AUTH4012",
      "유효하지 않은 리프레시 토큰입니다.",
      "리프레시 토큰 서명/형식 오류",
      ErrorLogLevel.WARN),
  AUTH_REFRESH_TOKEN_EXPIRED(
      HttpStatus.UNAUTHORIZED, "AUTH4013", "리프레시 토큰이 만료되었습니다.", "리프레시 토큰 만료", ErrorLogLevel.WARN),
  AUTH_REFRESH_TOKEN_REVOKED(
      HttpStatus.UNAUTHORIZED,
      "AUTH4014",
      "철회된 리프레시 토큰입니다.",
      "로그아웃/회전으로 무효화된 토큰",
      ErrorLogLevel.WARN),

   // Newsletter
  NEWSLETTER_NOT_FOUND(
      HttpStatus.NOT_FOUND,
       "NL4041",
       "가정통신문을 찾을 수 없습니다.",
       "newsletterId에 해당하는 레코드 없음 또는 소유권 불일치",
        ErrorLogLevel.INFO),
  NEWSLETTER_DUPLICATE(
      HttpStatus.CONFLICT,
      "NL4091",
      "이미 업로드된 가정통신문입니다.",
      "동일한 file_hash를 가진 가정통신문이 이미 존재함",
      ErrorLogLevel.WARN),
  NEWSLETTER_FILE_EMPTY(
      HttpStatus.BAD_REQUEST,
      "NL4001",
      "파일이 비어있습니다.",
      "업로드 파일 null 또는 empty",
      ErrorLogLevel.WARN),
  NEWSLETTER_FILE_TYPE_INVALID(
      HttpStatus.BAD_REQUEST,
      "NL4002",
      "지원하지 않는 파일 형식입니다. (jpg, png, pdf만 허용)",
      "허용되지 않는 Content-Type",
      ErrorLogLevel.WARN),
  NEWSLETTER_FILE_SIZE_EXCEEDED(
      HttpStatus.BAD_REQUEST,
      "NL4003",
      "파일 크기는 10MB 이하여야 합니다.",
      "10MB 초과 파일 업로드 시도",
      ErrorLogLevel.WARN),
  NEWSLETTER_FILE_READ_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "NL5001",
      "파일을 읽는 중 오류가 발생했습니다.",
      "SHA-256 해시 계산 중 InputStream 읽기 실패",
      ErrorLogLevel.ERROR),

  EXTERNAL_API_ERROR(
      HttpStatus.BAD_GATEWAY,
      "EXT5021",
      "외부 API 호출 중 오류가 발생했습니다.",
      "외부 연동 실패/타임아웃",
      ErrorLogLevel.ERROR),
  INTERNAL_SERVER_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "COMMON5001",
      "서버 내부 오류가 발생했습니다.",
      "처리되지 않은 예외",
      ErrorLogLevel.ERROR);

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
  private final String description;
  private final ErrorLogLevel logLevel;
}
