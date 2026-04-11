package com.gachi.be.global.config.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 app.clova.papago.* 값을 읽어옴.
 * - client-id: 네이버 개발자 센터에서 발급받은 클라이언트 ID
 * - client-secret: 클라이언트 시크릿
 * - api-url: 파파고 번역 API 엔드포인트 (고정값)
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.clova.papago")
public class PapagoProperties {
    private String clientId;
    private String clientSecret;
    private String apiUrl;
}
