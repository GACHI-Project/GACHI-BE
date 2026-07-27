package com.gachi.be.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 채팅 히스토리 Redis 관리 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRedisService {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  private static final String SESSION_KEY_PREFIX = "chat:session:";
  private static final Duration SESSION_TTL = Duration.ofHours(1);

  // 세션이 어떤 대화 범위(GENERAL / DOCUMENT:{newsletterId})에 묶여 있는지 저장하는 키 접미사.
  // 같은 sessionId로 GENERAL ↔ DOCUMENT를 섞어 쓰거나, A문서 세션으로 B문서를 물으면
  // 히스토리가 오염되어 엉뚱한 답변이 나가므로 이를 차단하기 위한 값
  private static final String SCOPE_KEY_SUFFIX = ":scope";
  // 최대 20턴 = 메시지 40개 (유저+AI 각 1개)
  // LTRIM으로 최근 40개만 유지 → 원자적 처리
  private static final int MAX_MESSAGES = 40;

  // 히스토리를 10턴(메시지 20개)으로 더 짧게 유지한다.
  public static final int MAX_DOCUMENT_MESSAGES = 20;

  /** 히스토리 전체 조회. */
  public List<Map<String, String>> getHistory(String sessionId) {
    String key = buildKey(sessionId);

    try {
      // opsForValue().get() → opsForList().range() 로 변경
      List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);

      if (jsonList == null || jsonList.isEmpty()) {
        log.debug("[ChatRedis] 히스토리 없음. sessionId={}", sessionId);
        return new ArrayList<>();
      }

      List<Map<String, String>> history = new ArrayList<>();
      for (String json : jsonList) {
        @SuppressWarnings("unchecked")
        Map<String, String> message = objectMapper.readValue(json, Map.class);
        history.add(message);
      }

      log.debug("[ChatRedis] 히스토리 조회. sessionId={}, size={}", sessionId, history.size());
      return history;

    } catch (Exception e) {
      log.error("[ChatRedis] 히스토리 파싱 실패. sessionId={}", sessionId, e);
      return new ArrayList<>();
    }
  }

  /** 유저 메시지 + AI 응답을 히스토리에 원자적으로 추가. */
  public void appendAndSave(String sessionId, String userMessage, String assistantReply) {
      // 상한값을 받는 오버로드로 위임. 기존 호출부는 그대로 동작
      appendAndSave(sessionId, userMessage, assistantReply, MAX_MESSAGES);
  }

  // 히스토리 상한(maxMessages)을 지정할 수 있는 오버로드.
  // GENERAL은 MAX_MESSAGES(40), DOCUMENT는 MAX_DOCUMENT_MESSAGES(20)를 사용한다.
  /** 유저 메시지 + AI 응답을 히스토리에 원자적으로 추가. */
  public void appendAndSave(
      String sessionId, String userMessage, String assistantReply, int maxMessages) {
      String key = buildKey(sessionId);

      try {
          String userJson =
              objectMapper.writeValueAsString(
                  Map.of("role", "user", "content", userMessage != null ? userMessage : ""));
          String assistantJson =
              objectMapper.writeValueAsString(
                  Map.of("role", "assistant", "content", assistantReply != null ? assistantReply : ""));

          // RPUSH로 원자적 추가 (동시 요청이 와도 각 RPUSH는 독립적으로 처리됨)
          redisTemplate.opsForList().rightPush(key, userJson);
          redisTemplate.opsForList().rightPush(key, assistantJson);

          // LTRIM으로 최근 maxMessages개만 유지 (음수 인덱스: -maxMessages ~ -1)
          redisTemplate.opsForList().trim(key, -maxMessages, -1);

          // TTL 갱신
          redisTemplate.expire(key, SESSION_TTL);

          log.debug("[ChatRedis] 히스토리 저장 완료. sessionId={}, maxMessages={}", sessionId, maxMessages);

      } catch (Exception e) {
          log.error("[ChatRedis] 히스토리 저장 실패. sessionId={}", sessionId, e);
      }
  }

  public String getSessionScope(String sessionId) {
      try {
          return redisTemplate.opsForValue().get(buildScopeKey(sessionId));
      } catch (Exception e) {
          log.error("[ChatRedis] 세션 scope 조회 실패. sessionId={}", sessionId, e);
          return null;
      }
  }

  /** 세션에 대화 범위를 바인딩한다. 히스토리와 동일한 TTL로 갱신된다. */
  public void saveSessionScope(String sessionId, String scope) {
      try {
          redisTemplate.opsForValue().set(buildScopeKey(sessionId), scope, SESSION_TTL);
          log.debug("[ChatRedis] 세션 scope 저장. sessionId={}, scope={}", sessionId, scope);
      } catch (Exception e) {
          log.error("[ChatRedis] 세션 scope 저장 실패. sessionId={}, scope={}", sessionId, scope, e);
      }
  }


  private String buildKey(String sessionId) {
    return SESSION_KEY_PREFIX + sessionId;
  }
    // scope 전용 키 생성. 히스토리 리스트 키(chat:session:{id})와 충돌하지 않는다.
    private String buildScopeKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId + SCOPE_KEY_SUFFIX;
    }
}
