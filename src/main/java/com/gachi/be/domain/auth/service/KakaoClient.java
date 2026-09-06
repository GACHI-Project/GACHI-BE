package com.gachi.be.domain.auth.service;

public interface KakaoClient {
  KakaoIdentity authenticate(String authorizationCode);

  void unlink(String providerUserId);

  record KakaoIdentity(String providerUserId, String email, String nickname, String phoneNumber) {}
}
