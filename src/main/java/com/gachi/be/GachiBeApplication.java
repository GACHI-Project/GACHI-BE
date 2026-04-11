package com.gachi.be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class GachiBeApplication {

  public static void main(String[] args) {
    SpringApplication.run(GachiBeApplication.class, args);
  }
}
