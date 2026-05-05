package com.gachi.be.domain.newsletter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;

/**
 * 가정통신문 분석 상태 조회(폴링) API의 응답 DTO.
 *
 * 프론트엔드가 주기적으로 이 API를 호출하여 분석 진행률을 확인, COMPLETED가 되면 결과 화면으로 이동.
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // null인 필드는 JSON 응답에서 제외
public record NewsletterStatusResponse(
    NewsletterStatus status, int progressPercent, String progressMessage, String errorMessage) {

  /**
   * 분석 상태에 따라 적절한 진행률과 에러메시지를 자동 계산하는 팩토리 메서드. TODO: 현재는 고정값으로 처리, 추후 AI 서버에서 단계별 진행률을 받아 세분화 예정
   */
  public static NewsletterStatusResponse of(NewsletterStatus status, String errorLog) {
    return switch (status) {
      case PENDING -> new NewsletterStatusResponse(status, 0, "문서를 준비하고 있어요", null);
      case PROCESSING -> new NewsletterStatusResponse(status, 60, "텍스트를 인식하고 번역하고 있어요", null);
      case COMPLETED -> new NewsletterStatusResponse(status, 100, "분석이 완료되었어요", null);
      case FAILED -> new NewsletterStatusResponse(status, 0, null, errorLog);
    };
  }
}
