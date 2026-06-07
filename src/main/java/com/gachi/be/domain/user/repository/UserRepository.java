package com.gachi.be.domain.user.repository;

import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  boolean existsByLoginId(String loginId);

  boolean existsByEmail(String email);

  boolean existsByPhoneNumber(String phoneNumber);

  Optional<User> findByLoginId(String loginId);

  Optional<User> findByEmail(String email);

  Optional<User> findByIdAndStatus(Long id, UserStatus status);

  List<User> findAllByStatus(UserStatus status);
}
