package com.gachi.be.domain.notification.repository;

import com.gachi.be.domain.notification.entity.Notification;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  @Query(
      """
      SELECT n FROM Notification n
      WHERE n.userId = :userId
        AND (:cursorId IS NULL OR n.id < :cursorId)
        AND (:unreadOnly = false OR n.readAt IS NULL)
        AND (
          :childId IS NULL
          OR n.childId = :childId
          OR (n.childId IS NULL AND n.childName = :childName)
        )
      ORDER BY n.id DESC
      """)
  List<Notification> findInbox(
      @Param("userId") Long userId,
      @Param("cursorId") Long cursorId,
      @Param("childId") Long childId,
      @Param("childName") String childName,
      @Param("unreadOnly") boolean unreadOnly,
      Pageable pageable);

  Optional<Notification> findByIdAndUserId(Long id, Long userId);

  Optional<Notification> findByUserIdAndDedupeKey(Long userId, String dedupeKey);

  List<Notification> findAllByUserIdAndIdIn(Long userId, Collection<Long> ids);

  long countByUserIdAndReadAtIsNull(Long userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE Notification n
      SET n.readAt = :readAt,
          n.updatedAt = :readAt
      WHERE n.userId = :userId
        AND n.readAt IS NULL
      """)
  int markAllReadByUserId(@Param("userId") Long userId, @Param("readAt") OffsetDateTime readAt);
}
