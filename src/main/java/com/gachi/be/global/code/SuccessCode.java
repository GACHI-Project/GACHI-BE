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
  AUTH_EMAIL_VERIFIED(HttpStatus.OK, "AUTH2004", "이메일 인증이 완료되었습니다."),
  AUTH_CHECK_LOGIN_ID_AVAILABLE(HttpStatus.OK, "AUTH2005", "사용 가능한 아이디입니다."),
  AUTH_CHECK_EMAIL_AVAILABLE(HttpStatus.OK, "AUTH2006", "사용 가능한 이메일입니다."),
  AUTH_CHECK_PHONE_NUMBER_AVAILABLE(HttpStatus.OK, "AUTH2007", "사용 가능한 전화번호입니다."),
  CHILD_CREATE_SUCCESS(HttpStatus.CREATED, "CHILD2011", "자녀 정보 등록에 성공하였습니다."),
  CHILD_GET_LIST_SUCCESS(HttpStatus.OK, "CHILD2001", "내 자녀 목록 조회에 성공하였습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
