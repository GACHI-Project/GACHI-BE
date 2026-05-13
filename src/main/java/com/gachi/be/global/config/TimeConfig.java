package com.gachi.be.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

  @Bean
  public Clock clock() {
    // 가정통신문의 상대 날짜는 학교 운영 지역인 한국 날짜를 기준으로 해석한다.
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
