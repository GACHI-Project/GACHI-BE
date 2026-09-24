package com.gachi.be.global.config.external;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.ai-server")
public class AiServerProperties {

  private String baseUrl = "http://localhost:8000";
  private int connectTimeoutSeconds = 10;
  private int readTimeoutSeconds = 120;

  @Min(1)
  @Max(10080)
  private int presignedUrlMinutes = 5;
}
