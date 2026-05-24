package com.gachi.be.domain.newsletter.repository;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  /** 가정통신문 목록 조회 (자녀 필터 + 제목 검색 + 페이지네이션). */
  @Query(
      value =
          """
        SELECT * FROM newsletter
        WHERE user_id = :userId
          AND (:childName IS NULL OR child_name = :childName)
          AND (:search IS NULL OR title LIKE CONCAT('%', CAST(:search AS TEXT), '%'))
        ORDER BY
          CASE WHEN :sort = 'oldest' THEN created_at END ASC,
          CASE WHEN :sort != 'oldest' THEN created_at END DESC
        """,
      countQuery =
          """
        SELECT COUNT(*) FROM newsletter
        WHERE user_id = :userId
          AND (:childName IS NULL OR child_name = :childName)
          AND (:search IS NULL OR title LIKE CONCAT('%', CAST(:search AS TEXT), '%'))
        """,
      nativeQuery = true)
  Page<Newsletter> findByUserIdWithFilters(
      @Param("userId") Long userId,
      @Param("childName") String childName,
      @Param("search") String search,
      @Param("sort") String sort,
      Pageable pageable);

  /** 홈화면용 최근 7일 가정통신문 조회. */
  @Query(
      """
        SELECT n FROM Newsletter n
        WHERE n.userId = :userId
          AND n.createdAt >= :rangeStart
          AND n.createdAt < :rangeEnd
        ORDER BY n.createdAt DESC
        """)
  List<Newsletter> findRecentByUserId(
      @Param("userId") Long userId,
      @Param("rangeStart") OffsetDateTime rangeStart,
      @Param("rangeEnd") OffsetDateTime rangeEnd);


  /** 언어 변경 시 진행중인 파이프라인 중단 처리용 쿼리*/
    @Modifying
    @Query(
        """
        UPDATE Newsletter n
        SET n.status = :failedStatus
        WHERE n.userId = :userId
          AND n.status IN :targetStatuses
        """)
    int cancelInProgressByUserId(
        @Param("userId") Long userId,
        @Param("targetStatuses") List<NewsletterStatus> targetStatuses,
        @Param("failedStatus") NewsletterStatus failedStatus);

}
