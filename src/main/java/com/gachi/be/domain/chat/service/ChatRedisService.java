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

  // 최대 20턴 = 메시지 40개 (유저+AI 각 1개)
  // LTRIM으로 최근 40개만 유지 → 원자적 처리
  private static final int MAX_MESSAGES = 40;

  /** 히스토리 전체 조회. */
  public List<Map<String, String>> getHistory(String sessionId) {
    String key = buildKey(sessionId);

    try {
      // [수정] opsForValue().get() → opsForList().range() 로 변경
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

      // LTRIM으로 최근 MAX_MESSAGES개만 유지 (음수 인덱스: -MAX_MESSAGES ~ -1)
      redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);

      // TTL 갱신
      redisTemplate.expire(key, SESSION_TTL);

      log.debug("[ChatRedis] 히스토리 저장 완료. sessionId={}", sessionId);

    } catch (Exception e) {
      log.error("[ChatRedis] 히스토리 저장 실패. sessionId={}", sessionId, e);
    }
  }

  private String buildKey(String sessionId) {
    return SESSION_KEY_PREFIX + sessionId;
  }
}
