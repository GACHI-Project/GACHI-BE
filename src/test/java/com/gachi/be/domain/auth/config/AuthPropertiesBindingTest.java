package com.gachi.be.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AuthPropertiesBindingTest {

  @Test
  void bindsKakaoPropertiesWithEnabledTrue() {
    MapConfigurationPropertySource source =
        new MapConfigurationPropertySource(
            Map.of(
                "app.auth.kakao.enabled", "true",
                "app.auth.kakao.rest-api-key", "test-rest-api-key",
                "app.auth.kakao.client-secret", "test-client-secret",
                "app.auth.kakao.admin-key", "test-admin-key",
                "app.auth.kakao.app-id", "test-app-id",
                "app.auth.kakao.redirect-uri", "https://example.test/kakao/callback"));

    AuthProperties.Kakao kakao =
        new Binder(source)
            .bind("app.auth.kakao", Bindable.of(AuthProperties.Kakao.class))
            .orElseThrow(() -> new IllegalStateException("Kakao properties were not bound"));

    assertThat(kakao.enabled()).isTrue();
    assertThat(kakao.restApiKey()).isEqualTo("test-rest-api-key");
    assertThat(kakao.clientSecret()).isEqualTo("test-client-secret");
    assertThat(kakao.adminKey()).isEqualTo("test-admin-key");
    assertThat(kakao.appId()).isEqualTo("test-app-id");
    assertThat(kakao.redirectUri()).isEqualTo("https://example.test/kakao/callback");
    assertThat(kakao.appRedirectUri()).isEqualTo("gachi://kakao-auth");
    assertThat(kakao.stateTtlSeconds()).isEqualTo(300);
    assertThat(kakao.ticketTtlSeconds()).isEqualTo(120);
    assertThat(kakao.signupTokenTtlSeconds()).isEqualTo(600);
  }
}
