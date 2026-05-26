package com.gachi.be.domain.notification.repository;

import com.gachi.be.domain.notification.entity.NotificationDeliveryLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryLogRepository
    extends JpaRepository<NotificationDeliveryLog, Long> {
  List<NotificationDeliveryLog> findAllByNotificationIdOrderByAttemptedAtAsc(Long notificationId);
}
