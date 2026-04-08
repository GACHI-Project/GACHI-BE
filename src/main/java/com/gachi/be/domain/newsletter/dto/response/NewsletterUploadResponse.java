package com.gachi.be.domain.newsletter.dto.response;

import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;

/**
 * 가정통신문 업로드 API의 응답 DTO.
 *
 * 업로드 직후 생성된 newsletter 레코드의 ID와 초기 상태(PENDING)를 반환한다.
 * 프론트엔드는 이 ID를 사용해서 폴링 API를 호출한다.
 *
 * @param newsletterId 생성된 가정통신문 레코드의 ID
 * @param status 현재 분석 상태 (업로드 직후에는 항상 PENDING)
 */
public record NewsletterUploadResponse(Long newsletterId, NewsletterStatus status) {}
