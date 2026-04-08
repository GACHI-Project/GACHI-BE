package com.gachi.be.domain.newsletter.dto.request;

/**
 * 가정통신문 업로드 API의 multipart/form-data 요청 파라미터를 담는 DTO.
 * 실제 파일(MultipartFile)은 컨트롤러에서 @RequestPart로 별도 수신
 * 여기는 파일 외 메타데이터(childId, childUnknown)만
 */
public record NewsletterUploadRequest(
    Long childId, // 자녀 미선택 시 null
    boolean childUnknown // 어느 아이인지 모름이면 true, 기본값 false
) {
    public NewsletterUploadRequest {
        // childUnknown이 전송되지 않으면 false (기본값)
    }
}
