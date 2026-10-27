package com.gachi.be.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GoogleAuthPropertiesBindingTest {

  @Test
  void bindsMultipleClientIdsAndTokenTtl() {
    MapConfigurationPropertySource source =
        new MapConfigurationPropertySource(
            Map.of(
                "app.auth.google.enabled", "true",
                "app.auth.google.client-ids", "web-client-id, android-client-id",
                "app.auth.google.signup-token-ttl-seconds", "1800"));

    GoogleAuthProperties properties =
        new Binder(source)
            .bind("app.auth.google", Bindable.of(GoogleAuthProperties.class))
            .orElseThrow(() -> new IllegalStateException("Google properties were not bound"));

    assertThat(properties.enabled()).isTrue();
    assertThat(properties.clientIds()).containsExactly("web-client-id", "android-client-id");
    assertThat(properties.signupTokenTtlSeconds()).isEqualTo(1800);
    assertThat(properties.isConfigurationValid()).isTrue();
  }
}
