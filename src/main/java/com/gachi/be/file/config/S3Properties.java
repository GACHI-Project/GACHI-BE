package com.gachi.be.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.s3")
public class S3Properties {
  private String region = "ap-northeast-2";
  private String bucket;
  private String publicBaseUrl;
  private String imagePrefix = "images";
}
