package com.gachi.be.domain.auth.config;

import com.gachi.be.domain.auth.service.AuthMailService;
import com.gachi.be.domain.auth.service.impl.NoopAuthMailService;
import com.gachi.be.domain.auth.service.impl.SmtpAuthMailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** 선택적 인프라 의존성이 있는 인증 빈을 등록한다. */
@Slf4j
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

  /** JavaMailSender 존재 여부에 따라 SMTP 발송기 또는 로그 대체 발송기를 선택한다. */
  @Bean
  public AuthMailService authMailService(
      ObjectProvider<JavaMailSender> javaMailSenderProvider, AuthProperties authProperties) {
    JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
    if (javaMailSender != null) {
      log.info("AuthMailService selected: SMTP sender");
      return new SmtpAuthMailService(javaMailSender, authProperties);
    }

    log.info("AuthMailService selected: Noop sender (SMTP not configured)");
    return new NoopAuthMailService();
  }
}
