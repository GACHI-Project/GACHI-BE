package com.gachi.be.domain.chat.service.impl;

import com.gachi.be.domain.chat.client.AiChatClient;
import com.gachi.be.domain.chat.dto.request.ChatMessageRequest;
import com.gachi.be.domain.chat.dto.response.ChatMessageResponse;
import com.gachi.be.domain.chat.service.ChatRedisService;
import com.gachi.be.domain.chat.service.ChatService;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

  // chatType 기본값: GENERAL (추후 DOCUMENT 추가 시 분기)
  private static final String DEFAULT_CHAT_TYPE = "GENERAL";

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

    // chatType 기본값 처리
    String chatType =
        (request.chatType() != null && !request.chatType().isBlank())
            ? request.chatType()
            : DEFAULT_CHAT_TYPE;

    // Redis에서 이전 히스토리 조회
    List<Map<String, String>> history = chatRedisService.getHistory(sessionId);

    log.info(
        "[ChatService] 채팅 요청. userId={}, sessionId={}, chatType={}, historySize={}",
        userId,
        sessionId,
        chatType,
        history.size());

    // AI 서버 호출
    String reply = aiChatClient.chat(request.message(), history, language, chatType);

    // AI 응답 시간 (KST)
    String sentAt = ZonedDateTime.now(KST).toString();

    // Redis 히스토리 업데이트 (유저 메시지 + AI 응답 추가)
    chatRedisService.appendAndSave(sessionId, request.message(), reply);

    log.info("[ChatService] 채팅 응답 완료. userId={}, sessionId={}", userId, sessionId);

    return new ChatMessageResponse(sessionId, reply, sentAt);
  }
}
