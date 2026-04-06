package com.gachi.be.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {
  OK(HttpStatus.OK, "OK200", "요청에 성공하였습니다."),
  CREATED(HttpStatus.CREATED, "OK201", "생성에 성공하였습니다."),
  AUTH_SIGNUP_SUCCESS(HttpStatus.CREATED, "AUTH2011", "회원가입이 완료되었습니다."),
  AUTH_LOGIN_SUCCESS(HttpStatus.OK, "AUTH2001", "로그인에 성공하였습니다."),
  AUTH_REISSUE_SUCCESS(HttpStatus.OK, "AUTH2002", "토큰 재발급에 성공하였습니다."),
  AUTH_EMAIL_CODE_SENT(HttpStatus.OK, "AUTH2003", "이메일 인증 코드가 발송되었습니다."),
  AUTH_EMAIL_VERIFIED(HttpStatus.OK, "AUTH2004", "이메일 인증이 완료되었습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
