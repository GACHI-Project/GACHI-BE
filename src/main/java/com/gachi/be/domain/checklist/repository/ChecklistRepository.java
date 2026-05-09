package com.gachi.be.domain.checklist.repository;

import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistRepository extends JpaRepository<Checklist, Long> {

  /** 특정 가정통신문의 모든 항목 조회 CHECKLIST & TODO */
  List<Checklist> findByNewsletterIdOrderByIdAsc(Long newsletterId);

  /** 특정 가정통신문의 type별 항목 조회. */
  List<Checklist> findByNewsletterIdAndTypeOrderByIdAsc(Long newsletterId, ChecklistType type);

  /** 특정 가정통신문의 모든 항목 삭제 */
  void deleteByNewsletterId(Long newsletterId);

  /** 특정 사용자의 특정 항목 조회 (소유권 검증 포함). */
  Optional<Checklist> findByIdAndUserId(Long id, Long userId);

  /** 특정 사용자의 미완료 CHECKLIST 항목 전체 조회. */
  List<Checklist> findByUserIdAndTypeAndCompletedFalse(Long userId, ChecklistType type);

    /** 특정 캘린더 일정에 연결된 CHECKLIST 타입 항목 조회.*/
    List<Checklist> findByCalendarEventIdAndTypeOrderByIdAsc(
        Long calendarEventId, ChecklistType type);

    /** 특정 캘린더 일정에 연결된 모든 CHECKLIST 항목 삭제. */
    void deleteByCalendarEventId(Long calendarEventId);
}
