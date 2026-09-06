package com.gachi.be.domain.auth.service;

import java.time.Duration;

public interface KakaoLoginStore {
  String issueState(Duration ttl);

  boolean consumeState(String state);

  String issue(String purpose, KakaoClient.KakaoIdentity identity, Duration ttl);

  KakaoClient.KakaoIdentity consume(String purpose, String token);
}
