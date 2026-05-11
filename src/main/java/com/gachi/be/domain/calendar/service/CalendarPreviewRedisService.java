package com.gachi.be.domain.calendar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.calendar.dto.CalendarPreviewEvent;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 캘린더 일정 등록 플로우에서 사용하는 Redis 임시 데이터 관리 서비스.
 *
 * <p>흐름 요약: 1. 가정통신문 AI 분석 완료 → AI 파이프라인에서 Redis에 preview 데이터 저장 (추후 연결) 2. GET /calendar/preview →
 * Redis에서 읽어 팝업에 표시 3. PATCH /calendar/dates → Redis에서 날짜 수정 후 다시 저장 4. POST /calendar → Redis 데이터
 * 기반으로 calendar_events insert → Redis 키 삭제
 *
 * Redis 키 형식: newsletter:preview:{newsletterId} - TTL: 1시간 (사용자가 팝업을 열고 이탈해도 자동 만료)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarPreviewRedisService {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  /** Redis 키 형식. {newsletterId}를 치환해서 사용. */
  private static final String PREVIEW_KEY_PREFIX = "newsletter:preview:";

  /** TTL 1시간: 사용자 이탈 시 자동 만료, 배치 정리 불필요. */
  private static final Duration PREVIEW_TTL = Duration.ofHours(1);

  /** preview 데이터 조회 */
  public List<CalendarPreviewEvent> getPreview(Long newsletterId) {
    String key = buildKey(newsletterId);
    String json = redisTemplate.opsForValue().get(key);

    if (json == null) {
      log.debug("[CalendarPreview] Redis 키 없음. newsletterId={}", newsletterId);
      return null;
    }

    try {
      List<CalendarPreviewEvent> events =
          objectMapper.readValue(json, new TypeReference<List<CalendarPreviewEvent>>() {});
      log.debug(
          "[CalendarPreview] preview 조회 성공. newsletterId={}, count={}",
          newsletterId,
          events.size());
      return events;
    } catch (Exception e) {
        log.error("[CalendarPreview] Redis 데이터 파싱 실패. newsletterId={}", newsletterId, e);
        throw new RuntimeException("캘린더 미리보기 데이터 파싱 실패", e);
    }
  }

  /** preview 데이터 저장 (신규 저장 또는 날짜 수정 후 덮어쓰기) */
  public void savePreview(Long newsletterId, List<CalendarPreviewEvent> events) {
    String key = buildKey(newsletterId);
    try {
      String json = objectMapper.writeValueAsString(events);
      redisTemplate.opsForValue().set(key, json, PREVIEW_TTL);
      log.debug(
          "[CalendarPreview] preview 저장 완료. newsletterId={}, count={}",
          newsletterId,
          events.size());
    } catch (Exception e) {
      log.error(
          "[CalendarPreview] Redis 저장 실패. newsletterId={}, error={}", newsletterId, e.getMessage());
      throw new RuntimeException("캘린더 미리보기 데이터 저장 실패", e);
    }
  }

  /** preview 데이터 삭제. */
  public void deletePreview(Long newsletterId) {
    String key = buildKey(newsletterId);
    redisTemplate.delete(key);
    log.debug("[CalendarPreview] preview 삭제 완료. newsletterId={}", newsletterId);
  }

  private String buildKey(Long newsletterId) {
    return PREVIEW_KEY_PREFIX + newsletterId;
  }
}
