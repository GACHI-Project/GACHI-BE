package com.gachi.be.domain.newsletter.entity.enums;

/**
 * 가정통신문 페이지 1장의 처리 상태.
 *
 * 정상 흐름: PENDING → OCR_DONE → SUCCESS
 *
 * PENDING : 아직 OCR 전. OCR_DONE : OCR 완료, 번역 대기. SUCCESS : OCR과 번역(한국어 사용자는 번역 생략)까지 완료.
 *
 * OCR_FAILED : 클로바 OCR 통신 실패(자동 1회 재시도 후에도 실패). UNREADABLE : 통신은 성공했지만 글자를 인식하지 못한 페이지(흐린 사진 등).
 * TRANSLATION_FAILED : Papago 번역 실패(자동 1회 재시도 후에도 실패). 세 상태는 문서가 PAUSED일 때 멈춘 페이지에서만 나타난다.
 *
 * SKIPPED : 건너뛴 페이지. 사용자가 건너뛰기를 눌렀거나, PDF에서 인식하지 못한 페이지가 자동으로 건너뛰어진 경우. originalText가 있으면 번역만
 * 건너뛴 것이고, 없으면 글자를 읽지 못한 페이지다.
 */
public enum NewsletterPageStatus {
    PENDING,
    OCR_DONE,
    SUCCESS,
    OCR_FAILED,
    UNREADABLE,
    TRANSLATION_FAILED,
    SKIPPED;

    /** OCR 단계에서 (다시) 처리해야 하는 상태인지. */
    public boolean needsOcr() {
        return this == PENDING || this == OCR_FAILED || this == UNREADABLE;
    }

    /** 번역 단계에서 (다시) 처리해야 하는 상태인지. */
    public boolean needsTranslation() {
        return this == OCR_DONE || this == TRANSLATION_FAILED;
    }

    /** 더 이상 처리할 것이 없는 최종 상태인지. (진행 페이지 수 계산용) */
    public boolean isFinished() {
        return this == SUCCESS || this == SKIPPED;
    }
}
