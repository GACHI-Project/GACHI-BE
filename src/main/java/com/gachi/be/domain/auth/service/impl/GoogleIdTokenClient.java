package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.config.GoogleAuthProperties;
import com.gachi.be.domain.auth.service.GoogleClient;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GoogleIdTokenClient implements GoogleClient {
  private final GoogleIdTokenVerifier verifier;

  @Autowired
  public GoogleIdTokenClient(GoogleAuthProperties properties) {
    this(
        new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(properties.clientIds())
            .build());
  }

  GoogleIdTokenClient(GoogleIdTokenVerifier verifier) {
    this.verifier = verifier;
  }

  @Override
  public GoogleIdentity verify(String idToken) {
    if (!StringUtils.hasText(idToken)) {
      throw invalidToken();
    }
    try {
      GoogleIdToken verifiedToken = verifier.verify(idToken.trim());
      if (verifiedToken == null) {
        throw invalidToken();
      }
      GoogleIdToken.Payload payload = verifiedToken.getPayload();
      return new GoogleIdentity(
          payload.getSubject(),
          payload.getEmail(),
          Boolean.TRUE.equals(payload.getEmailVerified()),
          payload.get("name") instanceof String name ? name : null);
    } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
      throw invalidToken();
    }
  }

  private BusinessException invalidToken() {
    return new BusinessException(ErrorCode.AUTH_GOOGLE_ID_TOKEN_INVALID);
  }
}
