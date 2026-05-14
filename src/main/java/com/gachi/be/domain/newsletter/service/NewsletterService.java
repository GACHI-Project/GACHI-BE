package com.gachi.be.domain.newsletter.service;

import com.gachi.be.domain.newsletter.dto.response.*;
import org.springframework.web.multipart.MultipartFile;

public interface NewsletterService {

  /**
   * 가정통신문 파일을 S3에 업로드하고 newsletter 레코드를 PENDING 상태로 생성.
   *
   * @param userId 현재 로그인한 사용자 ID
   * @param file 업로드할 파일 (jpg/png/pdf, 최대 10MB)
   * @param childId 연결할 자녀 ID (미선택 시 null)
   * @param userLanguage 사용자 언어 코드 (KO/US/ZH/VI)
   * @return newsletterId + status(PENDING)
   */
  NewsletterUploadResponse upload(
      Long userId, MultipartFile file, Long childId, String userLanguage);

  /**
   * 가정통신문의 현재 분석 상태와 진행률을 조회.
   *
   * @param userId 현재 로그인한 사용자 ID (소유권 검증용)
   * @param newsletterId 조회할 가정통신문 ID
   * @return status, progressPercent, errorMessage
   */
  NewsletterStatusResponse getStatus(Long userId, Long newsletterId);

  /** 번역 결과 조회 */
  NewsletterTranslationResponse getTranslation(Long userId, Long newsletterId);

  /**요약 결과 조회.*/
  NewsletterSummaryResponse getSummary(Long userId, Long newsletterId);

  /** 체크리스트 & 해야할 일 조회 */
  NewsletterChecklistResponse getChecklist(Long userId, Long newsletterId, String type);

  /** 가정통신문 상세 조회 */
  NewsletterDetailResponse getDetail(Long userId, Long newsletterId);

  /** 가정통신문 목록 조회 (자녀 필터 + 제목 검색 + 페이지네이션).*/
  NewsletterListResponse getList(
      Long userId, String childName, String search, int page, String sort);

  /** 홈화면 최근 7일 가정통신문 조회.*/
  NewsletterRecentResponse getRecent(Long userId);

}
