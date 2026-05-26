package com.gachi.be.domain.notification.repository;

import com.gachi.be.domain.notification.entity.PushDeviceToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, Long> {
  Optional<PushDeviceToken> findByUserIdAndTokenHash(Long userId, String tokenHash);

  Optional<PushDeviceToken> findByIdAndUserId(Long id, Long userId);

  List<PushDeviceToken> findAllByUserIdAndEnabledTrueAndDeletedAtIsNull(Long userId);
}
