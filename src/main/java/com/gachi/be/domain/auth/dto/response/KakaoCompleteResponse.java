package com.gachi.be.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KakaoCompleteResponse(
    Status status,
    AuthTokenResponse tokens,
    String signupToken,
    String linkToken,
    String email,
    String nickname) {
  public enum Status {
    LOGIN_SUCCESS,
    SIGNUP_REQUIRED,
    LINK_REQUIRED
  }

  public static KakaoCompleteResponse login(AuthTokenResponse tokens) {
    return new KakaoCompleteResponse(Status.LOGIN_SUCCESS, tokens, null, null, null, null);
  }

  public static KakaoCompleteResponse signup(String token, String email, String nickname) {
    return new KakaoCompleteResponse(Status.SIGNUP_REQUIRED, null, token, null, email, nickname);
  }

  public static KakaoCompleteResponse link(String token, String email) {
    return new KakaoCompleteResponse(Status.LINK_REQUIRED, null, null, token, email, null);
  }
}
