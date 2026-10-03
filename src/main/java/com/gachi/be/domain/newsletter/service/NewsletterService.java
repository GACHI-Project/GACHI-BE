package com.gachi.be.domain.newsletter.service;

import com.gachi.be.domain.newsletter.dto.response.*;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface NewsletterService {

  /**
   * 가정통신문 파일을 S3에 업로드하고 newsletter 레코드를 PENDING 상태로 생성.
   *
   * @param userId 현재 로그인한 사용자 ID
   * @param files 업로드할 파일 목록 (jpg/png 최대 10장 또는 pdf 1개, 장당 10MB / 총합 50MB)
   * @param childId 연결할 자녀 ID (미선택 시 null)
   * @return newsletterId + status(PENDING)
   */
  NewsletterUploadResponse upload(Long userId, List<MultipartFile> files, Long childId);

  /**
   * 가정통신문의 현재 분석 상태와 진행률을 조회.
   *
   * @param userId 현재 로그인한 사용자 ID (소유권 검증용)
   * @param newsletterId 조회할 가정통신문 ID
   * @return status, progressPercent, errorMessage
   */
  NewsletterStatusResponse getStatus(Long userId, Long newsletterId);

  /**
   * 실패한 가정통신문 분석을 다시 대기 상태로 되돌리고 파이프라인을 재실행합니다. 페이지 OCR 결과는 재사용하고 번역부터 다시 진행합니다. (OCR 결과가 없는 페이지는
   * OCR부터)
   */
  NewsletterUploadResponse retryAnalysis(Long userId, Long newsletterId);

  /**
   * 멈춘(PAUSED) 가정통신문을 멈춘 페이지부터 이어서 진행합니다. (멈춘 페이지 다시 시도) 멈춘 페이지가 다시 시도 불가 상태(인식 불가 + 다시 시도 1회 사용)이면
   * 파이프라인을 실행하지 않고 현재 PAUSED 상태를 그대로 반환합니다.
   *
   * @return newsletterId + status (실행 시작 시 PENDING, 실행하지 않은 경우 PAUSED)
   */
  NewsletterUploadResponse resumeAnalysis(Long userId, Long newsletterId);

  /**
   * 멈춘 페이지를 건너뛰고 다음 페이지부터 이어서 진행합니다.
   *
   * @param pageNo 건너뛸 페이지 번호 (1부터). 현재 멈춘 페이지여야 합니다.
   * @return newsletterId + status(PENDING)
   */
  NewsletterUploadResponse skipPage(Long userId, Long newsletterId, Integer pageNo);

  /** 번역 결과 조회 */
  NewsletterTranslationResponse getTranslation(Long userId, Long newsletterId);

  /** 요약 결과 조회. */
  NewsletterSummaryResponse getSummary(Long userId, Long newsletterId);

  /** 체크리스트 & 해야할 일 조회 */
  NewsletterChecklistResponse getChecklist(Long userId, Long newsletterId, String type);

  /** 가정통신문 상세 조회 */
  NewsletterDetailResponse getDetail(Long userId, Long newsletterId);

  /** 가정통신문 목록 조회 (자녀 필터 + 제목 검색 + 페이지네이션). */
  NewsletterListResponse getList(
      Long userId, String childName, String search, int page, String sort);

  /** 홈화면 최근 7일 가정통신문 조회. */
  NewsletterRecentResponse getRecent(Long userId);

  /** 자녀와의 대화 주제 추천 조회 */
  ConversationTopicResponse getConversationTopics(Long userId, Long newsletterId);

  /** 문화 맥락 안내(관련 학교 생활 가이드 FAQ) 조회. 최대 2개, 없으면 빈 배열. */
  NewsletterCulturalGuideResponse getCulturalGuides(Long userId, Long newsletterId);
}
