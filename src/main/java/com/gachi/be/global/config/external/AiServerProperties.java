package com.gachi.be.global.config.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai-server")
public class AiServerProperties {

  private String baseUrl = "http://localhost:8000";
  private int connectTimeoutSeconds = 10;
  private int readTimeoutSeconds = 120;
}
