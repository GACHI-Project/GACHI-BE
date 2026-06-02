package com.gachi.be.global.config.external;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** NEIS 학교기본정보 Open API 호출에 필요한 서버 설정을 관리한다. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.neis")
public class NeisProperties {
  private String apiKey;
  private String schoolApiKey;
  private String scheduleApiKey;

  @NotBlank private String schoolInfoUrl = "https://open.neis.go.kr/hub/schoolInfo";

  @NotBlank private String schoolScheduleUrl = "https://open.neis.go.kr/hub/SchoolSchedule";

  @Min(1)
  private int connectTimeoutSeconds = 5;

  @Min(1)
  private int readTimeoutSeconds = 10;

  public String getSchoolApiKey() {
    return hasText(schoolApiKey) ? schoolApiKey : apiKey;
  }

  public String getScheduleApiKey() {
    return hasText(scheduleApiKey) ? scheduleApiKey : apiKey;
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
