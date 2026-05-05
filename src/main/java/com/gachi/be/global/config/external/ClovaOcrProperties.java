package com.gachi.be.global.config.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 app.clova.ocr.* 값을 읽어오기 - invoke-url: 네이버 클라우드 플랫폼에서 발급받은 API Gateway URL -
 * secret-key: 도메인 생성 시 발급받은 Secret Key - presigned-url-minutes: 클로바가 S3에서 파일 다운로드할 Presigned URL 유효
 * 시간
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.clova.ocr")
public class ClovaOcrProperties {
  private String invokeUrl;
  private String secretKey;
  private int presignedUrlMinutes = 15;
}
