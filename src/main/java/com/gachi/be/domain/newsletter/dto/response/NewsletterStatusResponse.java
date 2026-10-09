package com.gachi.be.domain.newsletter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.NewsletterPage;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPausedReason;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPausedStage;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterSourceType;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import java.util.List;
import java.util.Objects;

/**
 * 가정통신문 분석 상태 조회(폴링) API의 응답 DTO.
 *
 * 프론트엔드가 주기적으로 이 API를 호출하여 분석 진행률을 확인, COMPLETED가 되면 결과 화면으로 이동.
 *
 * 페이지 정보(sourceType/totalPages/processedPages)와, PAUSED일 때 멈춘 페이지 정보 및 버튼 노출
 * 값(retryable/skippable)을 함께 내려준다. 프론트는 retryable/skippable 값대로 [다시 시도]/[건너뛰기] 버튼만 띄우면 된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // null인 필드는 JSON 응답에서 제외
public record NewsletterStatusResponse(
    NewsletterStatus status,
    int progressPercent,
    String progressMessage,
    String errorMessage,
    String failureStage,
    boolean canRetry,
    NewsletterSourceType sourceType,
    Integer totalPages,
    Integer processedPages,
    Integer pausedPageNo,
    NewsletterPausedStage pausedStage,
    NewsletterPausedReason pausedReason,
    Integer retryCount,
    Boolean retryable,
    Boolean skippable) {

  /**
   * 분석 상태에 따라 적절한 진행률과 에러메시지를 자동 계산하는 팩토리 메서드. TODO: 현재는 고정값으로 처리, 추후 AI 서버에서 단계별 진행률을 받아 세분화 예정
   *
   * 페이지 정보 없이 호출하면 페이지 목록이 빈 것으로 보고 계산한다. (기존 호출부/테스트 호환용)
   */
  public static NewsletterStatusResponse of(Newsletter newsletter) {
    return of(newsletter, List.of());
  }

  /** 페이지 목록까지 반영해 상태 응답을 만든다. */
  public static NewsletterStatusResponse of(Newsletter newsletter, List<NewsletterPage> pages) {
    NewsletterStatus status = newsletter.getStatus();
    NewsletterSourceType sourceType = newsletter.resolveSourceType();
    Integer totalPages = newsletter.getTotalPages();
    Integer processedPages =
        pages == null || pages.isEmpty()
            ? null
            : (int) pages.stream().filter(page -> page.getStatus().isFinished()).count();

    return switch (status) {
      case PENDING ->
          new NewsletterStatusResponse(
              status,
              0,
              "문서를 준비하고 있어요",
              null,
              null,
              false,
              sourceType,
              totalPages,
              processedPages,
              null,
              null,
              null,
              null,
              null,
              null);
      case PROCESSING ->
          new NewsletterStatusResponse(
              status,
              60,
              "텍스트를 인식하고 번역하고 있어요",
              null,
              null,
              false,
              sourceType,
              totalPages,
              processedPages,
              null,
              null,
              null,
              null,
              null,
              null);
      case PAUSED -> paused(newsletter, pages, sourceType, totalPages, processedPages);
      case COMPLETED ->
          new NewsletterStatusResponse(
              status,
              100,
              "분석이 완료되었어요",
              null,
              null,
              false,
              sourceType,
              totalPages,
              processedPages,
              null,
              null,
              null,
              null,
              null,
              null);
      case FAILED ->
          new NewsletterStatusResponse(
              status,
              0,
              null,
              "분석 중 오류가 발생했어요. 다시 분석을 시도할 수 있어요.",
              newsletter.getFailureStage(),
              true,
              sourceType,
              totalPages,
              processedPages,
              null,
              null,
              null,
              null,
              null,
              null);
    };
  }

  /** PAUSED 응답. 멈춘 페이지의 실패 사유와 다시 시도 횟수로 버튼 노출 값을 계산한다. */
  private static NewsletterStatusResponse paused(
      Newsletter newsletter,
      List<NewsletterPage> pages,
      NewsletterSourceType sourceType,
      Integer totalPages,
      Integer processedPages) {
    Integer pausedPageNo = newsletter.getPausedPageNo();
    NewsletterPage pausedPage =
        pages == null || pausedPageNo == null
            ? null
            : pages.stream()
                .filter(page -> Objects.equals(page.getPageNo(), pausedPageNo))
                .findFirst()
                .orElse(null);

    int retryCount = pausedPage != null ? pausedPage.getRetryCount() : 0;
    boolean retryable = pausedPage != null && pausedPage.isRetryable();
    boolean skippable = pausedPage != null && pausedPage.isSkippable();
    int progressPercent =
        totalPages != null && totalPages > 0 && processedPages != null
            ? processedPages * 100 / totalPages
            : 0;

    return new NewsletterStatusResponse(
        NewsletterStatus.PAUSED,
        progressPercent,
        pausedPageNo != null && totalPages != null
            ? pausedPageNo + " / " + totalPages + " 페이지에서 멈췄어요"
            : "처리가 잠시 멈췄어요",
        pausedErrorMessage(newsletter.getPausedReason(), pausedPageNo, retryable),
        null,
        false,
        sourceType,
        totalPages,
        processedPages,
        pausedPageNo,
        newsletter.getPausedStage(),
        newsletter.getPausedReason(),
        retryCount,
        retryable,
        skippable);
  }

  private static String pausedErrorMessage(
      NewsletterPausedReason reason, Integer pausedPageNo, boolean retryable) {
    String page = pausedPageNo != null ? pausedPageNo + "페이지" : "페이지";
    if (reason == null) {
      return page + " 처리 중 문제가 생겼어요.";
    }
    return switch (reason) {
      case UNREADABLE ->
          retryable
              ? page + " 글자를 읽지 못했어요. 다시 시도해 주세요."
              : page + " 글자를 읽지 못했어요. 이 페이지를 건너뛰고 계속할 수 있어요.";
      case OCR_FAILED -> page + " 글자 인식 중 문제가 생겼어요. 다시 시도해 주세요.";
      case TRANSLATION_FAILED -> page + " 번역 중 문제가 생겼어요. 다시 시도해 주세요.";
    };
  }
}
