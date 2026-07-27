package com.gachi.be.domain.chat.service.impl;

import com.gachi.be.domain.chat.client.AiChatClient;
import com.gachi.be.domain.chat.dto.request.ChatMessageRequest;
import com.gachi.be.domain.chat.dto.response.ChatMessageResponse;
import com.gachi.be.domain.chat.service.ChatRedisService;
import com.gachi.be.domain.chat.service.ChatService;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 채팅 서비스 구현체. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

  private final AiChatClient aiChatClient;
  private final ChatRedisService chatRedisService;
  private final UserRepository userRepository;
  private final NewsletterRepository newsletterRepository;

  // chatType 기본값: GENERAL
  private static final String DEFAULT_CHAT_TYPE = "GENERAL";

  private static final String CHAT_TYPE_GENERAL = "GENERAL";
  private static final String CHAT_TYPE_DOCUMENT = "DOCUMENT";
  private static final String SCOPE_DOCUMENT_PREFIX = "DOCUMENT:";

  // 응답 시간 KST 기준
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Override
  public ChatMessageResponse sendMessage(Long userId, ChatMessageRequest request) {

    // sessionId 없으면 신규 생성 (앱 진입 시 첫 메시지)
    String sessionId =
        (request.sessionId() != null && !request.sessionId().isBlank())
            ? request.sessionId()
            : UUID.randomUUID().toString();

    // 사용자 languageCode 조회 → AI 응답 언어 결정
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    String language = user.getLanguageCode(); // String 타입

    // chatType 기본값 처리 + 대소문자 정규화 + 허용값 검증 추가
    String chatType = normalizeChatType(request.chatType());

    // DOCUMENT 모드 검증 및 문서 컨텍스트 구성.
    // GENERAL이면 document는 null로 유지
    AiChatClient.DocumentContext document = null;
    Long documentNewsletterId = null;

    if (CHAT_TYPE_DOCUMENT.equals(chatType)) {
      if (request.newsletterId() == null) {
        throw new BusinessException(ErrorCode.CHAT_NEWSLETTER_ID_REQUIRED);
      }

      Newsletter newsletter =
          newsletterRepository
              .findById(request.newsletterId())
              .orElseThrow(() -> new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND));

      // 소유권 검증 (다른 사용자의 문서 열람 차단. 존재 여부 노출을 막기 위해 동일 에러 사용)
      if (!newsletter.getUserId().equals(userId)) {
        throw new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND);
      }

      // 분석이 끝나지 않았으면 본문이 없어 근거 없는 답변이 나갈 수 있으므로 차단
      if (newsletter.getStatus() != NewsletterStatus.COMPLETED
          || newsletter.getOriginalText() == null
          || newsletter.getOriginalText().isBlank()) {
        throw new BusinessException(ErrorCode.NEWSLETTER_NOT_COMPLETED);
      }

      documentNewsletterId = newsletter.getId();
      document =
          new AiChatClient.DocumentContext(
              newsletter.getId(),
              newsletter.getTitle(),
              newsletter.getSummary(),
              newsletter.getOriginalText());
    }

    // 세션 대화 범위 검증을 GET+SET(check-then-act)에서 원자적 바인딩 한 번으로 변경.
    // 기존에는 동일 sessionId로 서로 다른 chatType 요청이 동시에 들어오면
    // 둘 다 boundScope == null을 통과해 히스토리가 섞일 수 있었다(TOCTOU).
    String requestScope = buildSessionScope(chatType, documentNewsletterId);
    ChatRedisService.SessionScopeResult scopeResult =
        chatRedisService.bindSessionScope(sessionId, requestScope);

    if (scopeResult == ChatRedisService.SessionScopeResult.MISMATCH) {
      log.warn("[ChatService] 세션 대화 범위 불일치. sessionId={}, request={}", sessionId, requestScope);
      throw new BusinessException(ErrorCode.CHAT_SESSION_SCOPE_MISMATCH);
    }

    // Redis에서 이전 히스토리 조회
    List<Map<String, String>> history;
    if (scopeResult == ChatRedisService.SessionScopeResult.UNAVAILABLE) {
      log.warn("[ChatService] 세션 범위를 판별할 수 없어 히스토리 없이 처리합니다. sessionId={}", sessionId);
      history = List.of();
    } else {
      history = chatRedisService.getHistory(sessionId);
    }
    log.info(
        "[ChatService] 채팅 요청. userId={}, sessionId={}, chatType={}, newsletterId={},"
            + " historySize={}",
        userId,
        sessionId,
        chatType,
        documentNewsletterId,
        history.size());

    // AI 서버 호출
    String reply = aiChatClient.chat(request.message(), history, language, chatType, document);

    // AI 응답 시간 (KST)
    String sentAt = ZonedDateTime.now(KST).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    int maxMessages =
        CHAT_TYPE_DOCUMENT.equals(chatType)
            ? ChatRedisService.MAX_DOCUMENT_MESSAGES
            : ChatRedisService.MAX_MESSAGES;

    // Redis 히스토리 업데이트 (유저 메시지 + AI 응답 추가)
    chatRedisService.appendAndSave(sessionId, request.message(), reply, maxMessages);

    log.info("[ChatService] 채팅 응답 완료. userId={}, sessionId={}", userId, sessionId);

    return new ChatMessageResponse(sessionId, reply, sentAt);
  }

  // chatType 정규화 및 검증. null/blank는 GENERAL로 간주한다.
  private String normalizeChatType(String rawChatType) {
    if (rawChatType == null || rawChatType.isBlank()) {
      return DEFAULT_CHAT_TYPE;
    }
    String normalized = rawChatType.trim().toUpperCase();
    if (!CHAT_TYPE_GENERAL.equals(normalized) && !CHAT_TYPE_DOCUMENT.equals(normalized)) {
      throw new BusinessException(ErrorCode.CHAT_TYPE_INVALID);
    }
    return normalized;
  }

  /** 세션에 바인딩할 대화 범위 문자열 생성. GENERAL: "GENERAL" / DOCUMENT: "DOCUMENT:{newsletterId}" */
  private String buildSessionScope(String chatType, Long newsletterId) {
    if (CHAT_TYPE_DOCUMENT.equals(chatType)) {
      return SCOPE_DOCUMENT_PREFIX + newsletterId;
    }
    return CHAT_TYPE_GENERAL;
  }
}
