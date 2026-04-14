package com.gachi.be.global.config.external;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 외부 API 설정 Properties 등록. ClovaOcrProperties, PapagoProperties, 외부 API 설정 Properties를 Spring 빈으로
 * 등록하여 @Autowired / @RequiredArgsConstructor로 주입받게 함.
 */
@Configuration
@EnableConfigurationProperties({
  ClovaOcrProperties.class,
  PapagoProperties.class,
  OpenAiProperties.class
})
public class ExternalApiConfig {}
