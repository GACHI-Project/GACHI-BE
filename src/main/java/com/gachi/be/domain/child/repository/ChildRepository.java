package com.gachi.be.domain.child.repository;

import com.gachi.be.domain.child.entity.Child;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, Long> {
  List<Child> findAllByUserIdOrderByCreatedAtAsc(Long userId);

  long countByUserId(Long userId);

  List<Child> findByUserIdAndDeletedAtIsNull(Long userId);

  Optional<Child> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

  Optional<Child> findFirstByUserIdAndNameAndDeletedAtIsNull(Long userId, String name);
}
