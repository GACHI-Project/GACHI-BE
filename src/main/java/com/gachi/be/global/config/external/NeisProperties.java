package com.gachi.be.global.config.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** NEIS 학교기본정보 Open API 호출에 필요한 서버 설정을 관리한다. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.neis")
public class NeisProperties {
  private String apiKey;
  private String schoolInfoUrl = "https://open.neis.go.kr/hub/schoolInfo";
  private int connectTimeoutSeconds = 5;
  private int readTimeoutSeconds = 10;
}
