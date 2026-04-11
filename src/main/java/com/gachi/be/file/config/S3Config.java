package com.gachi.be.file.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {
  @Bean
  public S3Client s3Client(S3Properties s3Properties) {
      return S3Client.builder()
          .region(Region.of(s3Properties.getRegion()))
          .credentialsProvider(resolveCredentialsProvider(s3Properties)) // 자격증명 추가
          .build();
  }

  /**
  * S3Presigner 빈
  * 클로바 OCR 연동 시 Presigned URL을 생성할 때 사용 예정.
  * Base64 대신 URL로 전달.
  */
  @Bean
  public S3Presigner s3Presigner(S3Properties s3Properties) {
     return S3Presigner.builder()
         .region(Region.of(s3Properties.getRegion()))
         .credentialsProvider(resolveCredentialsProvider(s3Properties))
         .build();
    }
  /**
  * 자격증명 공급자 결정.
  * 환경변수 두 값이 모두 있으면 StaticCredentialsProvider,
  * 없으면 DefaultCredentialsProvider로 자동 탐색.
  */
  private AwsCredentialsProvider resolveCredentialsProvider(S3Properties s3Properties) {
      String accessKey = s3Properties.getAccessKeyId();
      String secretKey = s3Properties.getSecretAccessKey();

      if (accessKey != null && !accessKey.isBlank()
          && secretKey != null && !secretKey.isBlank()) {
          return StaticCredentialsProvider.create(
              AwsBasicCredentials.create(accessKey, secretKey));
      }
      return DefaultCredentialsProvider.create();
    }
}
