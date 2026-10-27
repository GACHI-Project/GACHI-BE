package com.gachi.be.domain.auth.service;

import java.time.Duration;

public interface GoogleLoginStore {
  String issue(String purpose, GoogleClient.GoogleIdentity identity, Duration ttl);

  GoogleClient.GoogleIdentity consume(String purpose, String token);
}
