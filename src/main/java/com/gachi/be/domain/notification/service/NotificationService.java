package com.gachi.be.domain.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.notification.dto.request.NotificationReadRequest;
import com.gachi.be.domain.notification.dto.request.PushTokenDeleteRequest;
import com.gachi.be.domain.notification.dto.request.PushTokenRegisterRequest;
import com.gachi.be.domain.notification.dto.response.NotificationListResponse;
import com.gachi.be.domain.notification.dto.response.NotificationReadResponse;
import com.gachi.be.domain.notification.dto.response.NotificationResponse;
import com.gachi.be.domain.notification.dto.response.NotificationUnreadCountResponse;
import com.gachi.be.domain.notification.dto.response.PushTokenResponse;
import com.gachi.be.domain.notification.entity.Notification;
import com.gachi.be.domain.notification.entity.NotificationDeliveryLog;
import com.gachi.be.domain.notification.entity.PushDeviceToken;
import com.gachi.be.domain.notification.entity.enums.NotificationDeliveryStatus;
import com.gachi.be.domain.notification.repository.NotificationDeliveryLogRepository;
import com.gachi.be.domain.notification.repository.NotificationRepository;
import com.gachi.be.domain.notification.repository.PushDeviceTokenRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 알림 보관함, 읽음 상태, 푸시 토큰 생명주기를 관리한다. */
@Service
@RequiredArgsConstructor
public class NotificationService {
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

  private final NotificationRepository notificationRepository;
  private final PushDeviceTokenRepository pushDeviceTokenRepository;
  private final NotificationDeliveryLogRepository notificationDeliveryLogRepository;
  private final ChildRepository childRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public NotificationListResponse getNotifications(
      Long userId, Long cursorId, Integer size, boolean unreadOnly, Long childId) {
    int pageSize = normalizePageSize(size);
    String childName = resolveChildName(userId, childId);
    List<Notification> rows =
        notificationRepository.findInbox(
            userId, cursorId, childId, childName, unreadOnly, PageRequest.of(0, pageSize + 1));

    boolean hasNext = rows.size() > pageSize;
    List<Notification> page = hasNext ? rows.subList(0, pageSize) : rows;
    Long nextCursor = hasNext && !page.isEmpty() ? page.get(page.size() - 1).getId() : null;

    return new NotificationListResponse(
        page.stream().map(this::toResponse).toList(), nextCursor, hasNext);
  }

  @Transactional(readOnly = true)
  public NotificationListResponse getNotifications(
      Long userId, Long cursorId, Integer size, boolean unreadOnly) {
    return getNotifications(userId, cursorId, size, unreadOnly, null);
  }

  @Transactional(readOnly = true)
  public NotificationUnreadCountResponse getUnreadCount(Long userId) {
    return new NotificationUnreadCountResponse(
        notificationRepository.countByUserIdAndReadAtIsNull(userId));
  }

  @Transactional
  public NotificationReadResponse markRead(Long userId, Long notificationId) {
    Notification notification =
        notificationRepository
            .findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
    boolean wasUnread = !notification.isRead();
    notification.markRead();
    return new NotificationReadResponse(wasUnread ? 1 : 0);
  }

  @Transactional
  public NotificationReadResponse markRead(Long userId, NotificationReadRequest request) {
    List<Notification> notifications =
        notificationRepository.findAllByUserIdAndIdIn(userId, request.notificationIds());
    int readCount = 0;
    for (Notification notification : notifications) {
      if (!notification.isRead()) {
        notification.markRead();
        readCount++;
      }
    }
    return new NotificationReadResponse(readCount);
  }

  @Transactional
  public NotificationReadResponse markAllRead(Long userId) {
    return new NotificationReadResponse(
        notificationRepository.markAllReadByUserId(userId, OffsetDateTime.now()));
  }

  @Transactional
  public PushTokenResponse registerPushToken(Long userId, PushTokenRegisterRequest request) {
    if (request == null || request.platform() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
    String token = requireToken(request.token());
    String tokenHash = sha256Hex(token);
    PushDeviceToken tokenEntity =
        pushDeviceTokenRepository
            .findByUserIdAndTokenHash(userId, tokenHash)
            .map(
                existing -> {
                  String deviceId =
                      preserveExistingIfBlank(request.deviceId(), existing.getDeviceId());
                  String appVersion =
                      preserveExistingIfBlank(request.appVersion(), existing.getAppVersion());
                  existing.refresh(request.platform(), token, tokenHash, deviceId, appVersion);
                  return existing;
                })
            .orElseGet(
                () ->
                    PushDeviceToken.builder()
                        .userId(userId)
                        .platform(request.platform())
                        .token(token)
                        .tokenHash(tokenHash)
                        .deviceId(normalizeOptional(request.deviceId()))
                        .appVersion(normalizeOptional(request.appVersion()))
                        .build());

    return toResponse(pushDeviceTokenRepository.save(tokenEntity));
  }

  @Transactional
  public void deletePushToken(Long userId, PushTokenDeleteRequest request) {
    if (request == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
    String tokenHash = sha256Hex(requireToken(request.token()));
    pushDeviceTokenRepository
        .findByUserIdAndTokenHash(userId, tokenHash)
        .ifPresent(PushDeviceToken::softDelete);
  }

  @Transactional
  public Notification createNotification(Long userId, NotificationCreateCommand command) {
    if (command == null
        || command.type() == null
        || !StringUtils.hasText(command.title())
        || !StringUtils.hasText(command.body())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
    String dedupeKey = normalizeOptional(command.dedupeKey());
    if (StringUtils.hasText(dedupeKey)) {
      var existing = notificationRepository.findByUserIdAndDedupeKey(userId, dedupeKey);
      if (existing.isPresent()) {
        return existing.get();
      }
    }

    Notification notification =
        Notification.builder()
            .userId(userId)
            .type(command.type())
            .level(command.level())
            .childId(command.childId())
            .childName(normalizeOptional(command.childName()))
            .title(normalizeRequired(command.title()))
            .body(normalizeRequired(command.body()))
            .payloadJson(serializePayload(command.payload()))
            .dedupeKey(dedupeKey)
            .build();

    try {
      Notification saved = notificationRepository.save(notification);
      eventPublisher.publishEvent(new NotificationCreatedEvent(saved.getId(), saved.getUserId()));
      return saved;
    } catch (DataIntegrityViolationException e) {
      // 동시에 같은 알림을 생성해도 사용자에게 중복 노출되지 않도록 DB unique 제약을 한 번 더 신뢰한다.
      if (StringUtils.hasText(dedupeKey)) {
        return notificationRepository
            .findByUserIdAndDedupeKey(userId, dedupeKey)
            .orElseThrow(() -> e);
      }
      throw e;
    }
  }

  @Transactional
  public void recordDeliveryResult(NotificationDeliveryResultCommand command) {
    Notification notification =
        notificationRepository
            .findById(command.notificationId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
    PushDeviceToken pushDeviceToken = null;
    if (command.pushDeviceTokenId() != null) {
      pushDeviceToken =
          pushDeviceTokenRepository.findById(command.pushDeviceTokenId()).orElse(null);
    }

    notificationDeliveryLogRepository.save(
        NotificationDeliveryLog.builder()
            .notification(notification)
            .pushDeviceToken(pushDeviceToken)
            .provider("MANUAL")
            .status(
                command.status() != null ? command.status() : NotificationDeliveryStatus.PENDING)
            .providerMessageId(normalizeOptional(command.providerMessageId()))
            .failureReason(normalizeOptional(command.failureReason()))
            .build());
  }

  private NotificationResponse toResponse(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getType(),
        notification.getLevel(),
        notification.getChildId(),
        notification.getChildName(),
        notification.getTitle(),
        notification.getBody(),
        deserializePayload(notification.getPayloadJson()),
        notification.isRead(),
        notification.getReadAt(),
        notification.getCreatedAt());
  }

  private PushTokenResponse toResponse(PushDeviceToken token) {
    return new PushTokenResponse(
        token.getId(),
        token.getPlatform(),
        token.getDeviceId(),
        token.getAppVersion(),
        token.isEnabled(),
        token.getLastRegisteredAt());
  }

  private String resolveChildName(Long userId, Long childId) {
    if (childId == null) {
      return null;
    }
    return childRepository
        .findByIdAndUserIdAndDeletedAtIsNull(childId, userId)
        .map(child -> normalizeOptional(child.getName()))
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
  }

  private int normalizePageSize(Integer size) {
    if (size == null) {
      return DEFAULT_PAGE_SIZE;
    }
    if (size < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }

  private String serializePayload(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
  }

  private Map<String, Object> deserializePayload(String payloadJson) {
    if (!StringUtils.hasText(payloadJson)) {
      return Collections.emptyMap();
    }
    try {
      return objectMapper.readValue(payloadJson, PAYLOAD_TYPE);
    } catch (Exception e) {
      Map<String, Object> fallback = new LinkedHashMap<>();
      fallback.put("raw", payloadJson);
      return fallback;
    }
  }

  private String normalizeRequired(String value) {
    return value == null ? "" : value.trim();
  }

  private String normalizeOptional(String value) {
    String normalized = normalizeRequired(value);
    return StringUtils.hasText(normalized) ? normalized : null;
  }

  private String requireToken(String token) {
    String normalized = normalizeRequired(token);
    if (!StringUtils.hasText(normalized)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
    return normalized;
  }

  private String preserveExistingIfBlank(String requestedValue, String existingValue) {
    String normalized = normalizeOptional(requestedValue);
    return normalized != null ? normalized : existingValue;
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm is not available", e);
    }
  }
}
