package com.gachi.be.domain.auth.service;

public interface GoogleClient {
  GoogleIdentity verify(String idToken);

  record GoogleIdentity(
      String providerUserId, String email, boolean emailVerified, String nickname) {}
}
