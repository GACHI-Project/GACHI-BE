package com.gachi.be.domain.auth.repository;

import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
  Optional<SocialAccount> findByProviderAndProviderUserId(
      SocialProvider provider, String providerUserId);

  Optional<SocialAccount> findByUserIdAndProvider(Long userId, SocialProvider provider);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select account from SocialAccount account where account.user.id = :userId and account.provider = :provider")
  Optional<SocialAccount> findByUserIdAndProviderForUpdate(
      @Param("userId") Long userId, @Param("provider") SocialProvider provider);

  void deleteByUserIdAndProvider(Long userId, SocialProvider provider);
}
