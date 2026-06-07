package com.gachi.be.domain.user.repository;

import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
  boolean existsByLoginId(String loginId);

  boolean existsByEmail(String email);

  boolean existsByPhoneNumber(String phoneNumber);

  Optional<User> findByLoginId(String loginId);

  Optional<User> findByEmail(String email);

  Optional<User> findByIdAndStatus(Long id, UserStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdWithLock(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.loginId = :loginId")
  Optional<User> findByLoginIdWithLock(@Param("loginId") String loginId);

  List<User> findAllByStatus(UserStatus status);
}
