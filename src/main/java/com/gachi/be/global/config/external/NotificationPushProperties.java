package com.gachi.be.global.config.external;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.notification.push")
public class NotificationPushProperties {

  private boolean enabled = false;
  private String provider = "expo";
  @Positive private int connectTimeoutSeconds = 5;
  @Positive private int readTimeoutSeconds = 10;
  private Expo expo = new Expo();

  @Getter
  @Setter
  public static class Expo {
    private String apiUrl = "https://exp.host/--/api/v2/push/send";
    private String accessToken;
  }
}
