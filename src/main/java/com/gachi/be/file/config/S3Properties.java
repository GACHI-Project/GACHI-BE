package com.gachi.be.file.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class S3Properties {
    private String region = "ap-northeast-2";
    private String bucket;
    private String publicBaseUrl;
    private String imagePrefix = "images";
}
