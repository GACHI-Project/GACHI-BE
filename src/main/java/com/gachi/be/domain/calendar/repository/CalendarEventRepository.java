package com.gachi.be.domain.calendar.repository;

import com.gachi.be.domain.calendar.entity.CalendarEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

  /** 특정 가정통신문에 등록된 일정이 존재하는지 확인. */
  boolean existsByNewsletterIdAndUserId(Long newsletterId, Long userId);

  /** 특정 가정통신문에 연결된 모든 일정 조회. */
  List<CalendarEvent> findByNewsletterIdAndUserId(Long newsletterId, Long userId);

  /** 월별 마커 조회: 시작, 종료, 접수 시작 중 하나가 해당 월에 있는 일정 반환. */
  @Query(
      """
      SELECT e FROM CalendarEvent e
      WHERE e.userId = :userId
        AND (:childName IS NULL OR e.childName = :childName)
        AND (
          (e.startAt >= :rangeStart AND e.startAt < :rangeEnd)
          OR (e.endAt >= :rangeStart AND e.endAt < :rangeEnd)
          OR (e.periodStartAt IS NOT NULL
              AND SUBSTRING(e.periodStartAt, 1, 10) >= :firstDate
              AND SUBSTRING(e.periodStartAt, 1, 10) < :nextMonthDate)
        )
      ORDER BY e.startAt ASC
      """)
  List<CalendarEvent> findMonthlyEndpointEvents(
      @Param("userId") Long userId,
      @Param("rangeStart") OffsetDateTime rangeStart,
      @Param("rangeEnd") OffsetDateTime rangeEnd,
      @Param("firstDate") String firstDate,
      @Param("nextMonthDate") String nextMonthDate,
      @Param("childName") String childName);

  /** 주별/날짜별 일정 조회: 기간의 양 끝이 날짜 범위에 있는 일정 반환. */
  @Query(
      """
      SELECT e FROM CalendarEvent e
      WHERE e.userId = :userId
        AND (:childName IS NULL OR e.childName = :childName)
        AND (
          (e.startAt >= :rangeStart AND e.startAt < :rangeEnd)
          OR (e.endAt >= :rangeStart AND e.endAt < :rangeEnd)
          OR (e.periodStartAt IS NOT NULL
              AND SUBSTRING(e.periodStartAt, 1, 10) >= :firstDate
              AND SUBSTRING(e.periodStartAt, 1, 10) < :nextDate)
        )
      ORDER BY e.startAt ASC
      """)
  List<CalendarEvent> findCalendarEndpointEventsInRange(
      @Param("userId") Long userId,
      @Param("rangeStart") OffsetDateTime rangeStart,
      @Param("rangeEnd") OffsetDateTime rangeEnd,
      @Param("firstDate") String firstDate,
      @Param("nextDate") String nextDate,
      @Param("childName") String childName);

  /** 체크리스트 등 기준일(startAt)에만 해당하는 일정 조회. */
  @Query(
      """
      SELECT e FROM CalendarEvent e
      WHERE e.userId = :userId
        AND e.startAt >= :rangeStart
        AND e.startAt < :rangeEnd
        AND (:childName IS NULL OR e.childName = :childName)
      ORDER BY e.startAt ASC
      """)
  List<CalendarEvent> findEventsInRange(
      @Param("userId") Long userId,
      @Param("rangeStart") OffsetDateTime rangeStart,
      @Param("rangeEnd") OffsetDateTime rangeEnd,
      @Param("childName") String childName);

  /** external_key로 일정 조회 (중복 등록 방지용). */
  Optional<CalendarEvent> findByExternalKey(String externalKey);

  /** 소유권 검증 포함 단건 조회. */
  Optional<CalendarEvent> findByIdAndUserId(Long id, Long userId);

  List<CalendarEvent> findByStartAtGreaterThanEqualAndStartAtLessThan(
      OffsetDateTime rangeStart, OffsetDateTime rangeEnd);

  long countByUserIdAndStartAtGreaterThanEqualAndStartAtLessThan(
      Long userId, OffsetDateTime rangeStart, OffsetDateTime rangeEnd);

  /** 특정 가정통신문의 모든 일정 삭제. */
  void deleteByNewsletterIdAndUserId(Long newsletterId, Long userId);

  @Query(
      """
        SELECT DISTINCT e.newsletterId FROM CalendarEvent e
        WHERE e.userId = :userId
          AND e.newsletterId IN :newsletterIds
        """)
  List<Long> findRegisteredNewsletterIds(
      @Param("userId") Long userId, @Param("newsletterIds") List<Long> newsletterIds);

  // 자녀 이름 변경 시 calendar_events.child_name 일괄 동기화
  @Modifying
  @Query(
      """
        UPDATE CalendarEvent e
        SET e.childName = :newName
        WHERE e.userId = :userId AND e.childName = :oldName
        """)
  void updateChildNameByUserIdAndOldName(
      @Param("userId") Long userId,
      @Param("oldName") String oldName,
      @Param("newName") String newName);

  // 자녀 색상 변경 시 calendar_events.child_color 일괄 동기화
  @Modifying
  @Query(
      """
        UPDATE CalendarEvent e
        SET e.childColor = :newColor
        WHERE e.userId = :userId AND e.childName = :childName
        """)
  void updateChildColorByUserIdAndChildName(
      @Param("userId") Long userId,
      @Param("childName") String childName,
      @Param("newColor") String newColor);

  // 자녀 삭제 시 해당 자녀의 모든 calendar_events 삭제
  void deleteAllByUserIdAndChildName(Long userId, String childName);
}
