package com.gachi.be.domain.newsletter.service;

import com.gachi.be.domain.newsletter.dto.response.NewsletterStatusResponse;
import com.gachi.be.domain.newsletter.dto.response.NewsletterUploadResponse;
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
}
