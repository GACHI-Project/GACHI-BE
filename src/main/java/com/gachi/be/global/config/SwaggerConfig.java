package com.gachi.be.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"dev", "stage"})
public class SwaggerConfig {

  @Bean
  public OpenAPI gachiOpenApi() {
    String schemeName = "bearerAuth";
    return new OpenAPI()
        .info(new Info().title("GACHI-BE API").description("GACHI 백엔드 API 문서").version("v1"))
        .components(
            new Components()
                .addSecuritySchemes(
                    schemeName,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(schemeName));
  }
}
