package com.gachi.be.domain.child.repository;

import com.gachi.be.domain.child.entity.Child;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, Long> {

    /**
     * 특정 사용자의 활성 자녀 목록 조회
     */
    List<Child> findByUserIdAndDeletedAtIsNull(Long userId);

    /**
     * 특정 사용자의 특정 자녀 조회
     */
    Optional<Child> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
