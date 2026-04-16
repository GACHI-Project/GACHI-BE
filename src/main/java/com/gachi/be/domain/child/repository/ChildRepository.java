package com.gachi.be.domain.child.repository;

import com.gachi.be.domain.child.entity.Child;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, Long> {
  List<Child> findAllByUserIdOrderByCreatedAtAsc(Long userId);

  long countByUserId(Long userId);
}
