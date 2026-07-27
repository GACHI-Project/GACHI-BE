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
  public static final int MAX_MESSAGES = 40;

  // 히스토리를 10턴(메시지 20개)으로 더 짧게 유지한다.
  public static final int MAX_DOCUMENT_MESSAGES = 20;

  // 세션 대화 범위 바인딩 결과.
  // 기존에는 getSessionScope()가 "미바인딩"과 "Redis 조회 실패"를 둘 다 null로 반환해서
  // 호출부가 구분할 수 없었고, 조회 실패 시 검증이 그냥 통과되는 문제가 있었다.
  public enum SessionScopeResult {
      /** 이번 요청 범위로 바인딩 완료 (신규 바인딩 또는 동일 범위 재사용). */
      BOUND,
      /** 이미 다른 범위로 바인딩된 세션. 요청을 거부해야 한다. */
      MISMATCH,
      /** Redis 장애 등으로 범위를 판별할 수 없음. 히스토리를 사용하지 말아야 한다. */
      UNAVAILABLE
  }

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

  // 세션 대화 범위를 원자적으로 바인딩한다.
  // 최초 바인딩은 Redis SETNX(setIfAbsent)로 처리하므로 동시 요청 중 하나만 성공한다.
  /**
   * 세션에 대화 범위를 바인딩하고 결과를 반환한다.
   *
   * 이미 같은 범위로 바인딩되어 있으면 TTL만 갱신하고 {@link SessionScopeResult#BOUND}를 반환한다. 다른 범위로 바인딩되어
   * 있으면 {@link SessionScopeResult#MISMATCH}를 반환한다.
   *
   * @param scope "GENERAL" 또는 "DOCUMENT:{newsletterId}"
   */
  public SessionScopeResult bindSessionScope(String sessionId, String scope) {
      String key = buildScopeKey(sessionId);

      try {
          // SETNX + TTL을 한 번의 명령으로 실행 → 최초 바인딩 경쟁을 Redis가 직렬화한다.
          Boolean created = redisTemplate.opsForValue().setIfAbsent(key, scope, SESSION_TTL);
          if (Boolean.TRUE.equals(created)) {
              log.debug("[ChatRedis] 세션 scope 신규 바인딩. sessionId={}, scope={}", sessionId, scope);
              return SessionScopeResult.BOUND;
          }

          String boundScope = redisTemplate.opsForValue().get(key);

          // setIfAbsent 실패 직후 TTL 만료 등으로 키가 사라진 경우 → 다시 원자적으로 시도
          if (boundScope == null) {
              Boolean retried = redisTemplate.opsForValue().setIfAbsent(key, scope, SESSION_TTL);
              return Boolean.TRUE.equals(retried)
                  ? SessionScopeResult.BOUND
                  : SessionScopeResult.MISMATCH;
          }

          if (!boundScope.equals(scope)) {
              log.warn(
                  "[ChatRedis] 세션 scope 불일치. sessionId={}, bound={}, request={}",
                  sessionId,
                  boundScope,
                  scope);
              return SessionScopeResult.MISMATCH;
          }

          // 같은 범위의 후속 요청 → TTL만 연장
          redisTemplate.expire(key, SESSION_TTL);
          return SessionScopeResult.BOUND;

      } catch (Exception e) {
          // 예외를 전파하면 Redis 장애 시 챗봇 전체가 죽는다.
          // UNAVAILABLE을 반환하고, 호출부가 히스토리를 쓰지 않는 방식으로 안전하게 처리한다.
          log.error("[ChatRedis] 세션 scope 바인딩 실패. sessionId={}, scope={}", sessionId, scope, e);
          return SessionScopeResult.UNAVAILABLE;
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
