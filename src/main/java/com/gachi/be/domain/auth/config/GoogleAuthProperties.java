package com.gachi.be.domain.auth.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth.google")
public record GoogleAuthProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue List<String> clientIds,
    @DefaultValue("1800") @Min(1) long signupTokenTtlSeconds) {

  public GoogleAuthProperties {
    clientIds =
        clientIds == null
            ? List.of()
            : clientIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
  }

  @AssertTrue(message = "Google OAuth client IDs are required when enabled.")
  public boolean isConfigurationValid() {
    return !enabled || !clientIds.isEmpty();
  }
}
