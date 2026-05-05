package com.gachi.be.domain.newsletter.dto.response;

import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;

/** 가정통신문 업로드 API의 응답 DTO.*/
public record NewsletterUploadResponse(Long newsletterId, NewsletterStatus status) {}
