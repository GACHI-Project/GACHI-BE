package com.gachi.be.domain.auth.repository;

import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
  Optional<SocialAccount> findByProviderAndProviderUserId(
      SocialProvider provider, String providerUserId);

  Optional<SocialAccount> findByUserIdAndProvider(Long userId, SocialProvider provider);

  void deleteByUserIdAndProvider(Long userId, SocialProvider provider);
}
