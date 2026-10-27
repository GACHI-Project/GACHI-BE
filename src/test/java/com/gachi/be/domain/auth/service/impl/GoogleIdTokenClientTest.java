package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.auth.service.GoogleClient;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;

class GoogleIdTokenClientTest {

  @Test
  void returnsOnlyVerifiedIdentityClaims() throws Exception {
    GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
    GoogleIdToken token = mock(GoogleIdToken.class);
    GoogleIdToken.Payload payload =
        new GoogleIdToken.Payload()
            .setSubject("google-sub")
            .setEmail("user@gachi.com")
            .setEmailVerified(true);
    payload.set("name", "민주");
    when(verifier.verify("id-token")).thenReturn(token);
    when(token.getPayload()).thenReturn(payload);

    GoogleClient.GoogleIdentity identity = new GoogleIdTokenClient(verifier).verify(" id-token ");

    assertThat(identity.providerUserId()).isEqualTo("google-sub");
    assertThat(identity.email()).isEqualTo("user@gachi.com");
    assertThat(identity.emailVerified()).isTrue();
    assertThat(identity.nickname()).isEqualTo("민주");
  }

  @Test
  void rejectsTokenWhenGoogleVerificationFails() throws Exception {
    GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
    when(verifier.verify("invalid-token")).thenReturn(null);

    assertThatThrownBy(() -> new GoogleIdTokenClient(verifier).verify("invalid-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_GOOGLE_ID_TOKEN_INVALID));
  }
}
