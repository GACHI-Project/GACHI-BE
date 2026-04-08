package com.gachi.be.domain.newsletter.entity.enums;

/**
 * 가정통신문 AI 분석 파이프라인의 진행 상태를 나타내는 Enum.
 * 상태 전이 흐름: PENDING → PROCESSING → COMPLETED (또는 FAILED)
 *  PENDING : 파일 업로드 직후 초기 상태. AI 분석 대기 중.
 *  PROCESSING : AI 서버가 OCR/번역/요약 작업을 진행 중인 상태.
 *  COMPLETED : 모든 AI 분석이 완료된 상태. 결과 조회 가능.
 *  FAILED : 분석 도중 오류가 발생한 상태. error_log 컬럼에 사유 저장.
 */
public enum NewsletterStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
