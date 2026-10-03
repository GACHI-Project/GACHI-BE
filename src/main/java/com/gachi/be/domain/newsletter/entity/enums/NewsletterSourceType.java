package com.gachi.be.domain.newsletter.entity.enums;

/**
 * 가정통신문 원본 파일 종류.
 *
 * PDF : PDF 1개 업로드. 클로바 OCR이 PDF를 통째로 처리하고 페이지별 결과를 돌려준다. 화면은 페이지별 원문/번역 텍스트로 보여준다.
 *
 * IMAGE : jpg/png 1~10장 업로드. 장마다 OCR을 호출하고, 화면은 이미지 위에 블록별 번역을 덮어 보여준다(오버레이).
 */
public enum NewsletterSourceType {
    PDF,
    IMAGE;

    /** 파일 키 확장자로 원본 종류를 판단한다. source_type 컬럼이 없던 과거 문서에도 그대로 사용할 수 있다. */
    public static NewsletterSourceType fromFileKey(String fileKey) {
        if (fileKey != null && fileKey.toLowerCase().endsWith(".pdf")) {
            return PDF;
        }
        return IMAGE;
    }
}
