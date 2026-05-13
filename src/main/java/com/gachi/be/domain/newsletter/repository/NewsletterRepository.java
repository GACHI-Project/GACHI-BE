package com.gachi.be.domain.newsletter.repository;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 가정통신문(newsletter) 테이블 JPA 레포지토리. */
public interface NewsletterRepository extends JpaRepository<Newsletter, Long> {

  /** 자녀가 특정된 가정통신문 중 동일 파일 해시 존재 여부 확인 (중복 방지). */
  Optional<Newsletter> findByUserIdAndChildNameAndFileHash(
      Long userId, String childName, String fileHash);

  /** 자녀 미선택(child_name=NULL) 가정통신문 중 동일 파일 해시 존재 여부 확인 (중복 방지). */
  Optional<Newsletter> findByUserIdAndChildNameIsNullAndFileHash(Long userId, String fileHash);

  /** 특정 사용자·자녀 이름의 모든 newsletter의 child_color를 일괄 업데이트. */
  @Modifying
  @Query(
      """
      UPDATE Newsletter n
      SET n.childColor = :newColor
      WHERE n.userId = :userId AND n.childName = :childName
      """)
  void updateChildColorByUserIdAndChildName(
      @Param("userId") Long userId,
      @Param("childName") String childName,
      @Param("newColor") String newColor);
}
