package com.gachi.be.domain.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**채팅 히스토리 Redis 관리 서비스.*/
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis 키 prefix
    private static final String SESSION_KEY_PREFIX = "chat:session:";

    // TTL 1시간: 프론트에서 sessionId를 버리면 자연 만료
    private static final Duration SESSION_TTL = Duration.ofHours(1);

    // 토큰 폭발 방지: 히스토리 최대 20턴(유저+AI 각 1개 = 1턴)
    private static final int MAX_HISTORY_TURNS = 20;

    /** 히스토리 조회. 키 없으면 빈 리스트 반환. */
    public List<Map<String, String>> getHistory(String sessionId) {
        String key = buildKey(sessionId);
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            log.debug("[ChatRedis] 히스토리 없음. sessionId={}", sessionId);
            return new ArrayList<>();
        }

        try {
            List<Map<String, String>> history =
                objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            log.debug("[ChatRedis] 히스토리 조회. sessionId={}, size={}", sessionId, history.size());
            return history;
        } catch (Exception e) {
            log.error("[ChatRedis] 히스토리 파싱 실패. sessionId={}", sessionId, e);
            // 파싱 실패 시 새 대화로 처리 (파이프라인 중단하지 않음)
            return new ArrayList<>();
        }
    }

    /**
     * 유저 메시지 + AI 응답을 히스토리에 추가 후 저장.
     * MAX_HISTORY_TURNS 초과 시 오래된 턴부터 제거.
     */
    public void appendAndSave(String sessionId, String userMessage, String assistantReply) {
        List<Map<String, String>> history = getHistory(sessionId);

        // 유저 메시지 추가
        history.add(Map.of("role", "user", "content", userMessage));
        // AI 응답 추가
        history.add(Map.of("role", "assistant", "content", assistantReply));

        // 최대 턴 수 초과 시 앞에서부터 2개씩 제거 (가장 오래된 1턴)
        while (history.size() > MAX_HISTORY_TURNS * 2) {
            history.remove(0);
            history.remove(0);
        }

        try {
            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(buildKey(sessionId), json, SESSION_TTL);
            log.debug("[ChatRedis] 히스토리 저장. sessionId={}, size={}", sessionId, history.size());
        } catch (Exception e) {
            // 저장 실패해도 이미 응답은 반환됐으므로 로그만
            log.error("[ChatRedis] 히스토리 저장 실패. sessionId={}", sessionId, e);
        }
    }

    /** TTL 갱신 (응답 성공 시 세션 연장) */
    public void refreshTtl(String sessionId) {
        redisTemplate.expire(buildKey(sessionId), SESSION_TTL);
    }

    private String buildKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }
}
