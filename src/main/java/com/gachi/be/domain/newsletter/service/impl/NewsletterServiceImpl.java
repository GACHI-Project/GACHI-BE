package com.gachi.be.domain.newsletter.service.impl;

import com.gachi.be.domain.child.entity.Child;
import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.newsletter.dto.response.NewsletterStatusResponse;
import com.gachi.be.domain.newsletter.dto.response.NewsletterUploadResponse;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.newsletter.service.NewsletterService;
import com.gachi.be.file.service.S3FileService;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 흐름:
 * 파일 유효성 검사 → SHA-256 해시 계산 → 중복 확인 → S3 업로드
 * → 자녀 정보 스냅샷 조회 → newsletter DB 저장(PENDING)
 * childId 대신 child_name/child_grade/child_color 스냅샷 저장
 * 중복 체크 기준: (user_id, child_name, file_hash) 또는 (user_id, file_hash)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterServiceImpl implements NewsletterService {

    private final NewsletterRepository newsletterRepository;
    private final ChildRepository childRepository;
    private final S3FileService s3FileService;

    /**
     * 가정통신문 파일을 S3에 업로드하고 newsletter 레코드를 PENDING 상태로 생성한다.
     *
     * 처리 순서:
     *   파일 유효성 검사 (형식: jpg/png/pdf, 크기: 최대 10MB)
     *   SHA-256 해시 계산 (중복 방지용)
     *   중복 파일 확인
     *   S3 업로드 → file_key 획득
     *   childId가 있으면 children 테이블에서 자녀 정보 조회 (스냅샷용)
     *   newsletter 레코드 DB 저장 (status=PENDING 으로 변경)
     */
    @Override
    @Transactional
    public NewsletterUploadResponse upload(
        Long userId, MultipartFile file, Long childId, String userLanguage) {

        // 파일 유효성 검사
        validateFile(file);

        // SHA-256 해시 계산
        // 파일 내용 전체를 읽어 고유한 해시값 생성
        // 동일 파일 재업로드를 감지하는 핵심 수단으로 진행
        String fileHash = computeSha256(file);
        log.debug("[Newsletter] 해시 계산 완료. userId={}, fileHash={}", userId, fileHash);

        // 자녀 정보 조회 (스냅샷용)
        String childName = null;
        Integer childGrade = null;
        String childColor = null;

        if (childId != null) {
            // 해당 사용자 소유의 활성 자녀인지 확인
            Child child = childRepository
                .findByIdAndUserIdAndDeletedAtIsNull(childId, userId)
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE, "존재하지 않는 자녀입니다. childId=" + childId));

            // 업로드 시점의 값을 복사 (이후 child 정보가 변경되어도 여기는 유지)
            childName = child.getName();
            childGrade = child.getGrade();
            childColor = child.getCalendarColor(); // 색상만 예외적으로 나중에 동기화 대상
        }

        // 중복 파일 확인
        // 자녀 특정 시: (user_id + child_name + file_hash) 조합으로 확인
        // 자녀 미선택 시: (user_id + file_hash) 조합으로 확인
        checkDuplicate(userId, childName, fileHash);

        // S3 업로드 - 가정통신문 전용 경로에 저장 + 디버깅 로그 추가해서 체크
        String fileKey = s3FileService.upload(file);
        log.debug("[Newsletter] S3 업로드 완료. userId={}, fileKey={}", userId, fileKey);

        // newsletter 레코드 DB 저장
        // AI 분석 결과 컬럼들(ocrText 등)은 현재 NULL처릴를 진행하고 비동기 파이프라인이 완료되면 채워지게 진행
        Newsletter newsletter = Newsletter.builder()
            .userId(userId)
            .childName(childName)
            .childGrade(childGrade)
            .childColor(childColor)
            .fileKey(fileKey)
            .fileHash(fileHash)
            .status(NewsletterStatus.PENDING)
            .language(userLanguage != null ? userLanguage : "KO")
            .build();

        Newsletter saved = newsletterRepository.save(newsletter);
        log.info("[Newsletter] 업로드 완료. userId={}, newsletterId={}", userId, saved.getId());

        return new NewsletterUploadResponse(saved.getId(), saved.getStatus());
    }

    /**
     * 가정통신문의 현재 분석 상태와 진행률을 반환.
     * 프론트엔드 스캔 중 화면에서 2초마다 이 API를 호출(폴링)하여 진행률을 표시.
     * TODO : 추후 AI 서버에서 단계별 콜백을 받으면 세분화 시켜서 진행률 반환
     * 현재는 상태별 고정 진행률로 반환
     */
    @Override
    @Transactional(readOnly = true)
    public NewsletterStatusResponse getStatus(Long userId, Long newsletterId) {
        Newsletter newsletter = findNewsletterById(newsletterId);
        validateOwnership(newsletter, userId);
        return NewsletterStatusResponse.of(newsletter.getStatus(), null);
    }

    /**
     * 파일 유효성 검사.
     *
     * TODO: 허용방식은 일단 이렇게만 지정해두고 테스트 해보면서 추가할 지 고려.
     * 허용 형식: image/jpeg, image/png, application/pdf
     * 최대 크기: 10MB
     *
     * @throws BusinessException 파일이 null/비어있거나 허용되지 않는 형식/크기일 때
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.NEWSLETTER_FILE_EMPTY);
        }

        String contentType = file.getContentType();
        boolean allowed = contentType != null
            && (contentType.equals("image/jpeg")
            || contentType.equals("image/png")
            || contentType.equals("application/pdf"));

        if (!allowed) {
            throw new BusinessException(
                ErrorCode.NEWSLETTER_FILE_TYPE_INVALID);
        }

        if (file.getSize() > 10 * 1024 * 1024L) {
            throw new BusinessException(ErrorCode.NEWSLETTER_FILE_SIZE_EXCEEDED);
        }
    }

    /**
     * 파일의 SHA-256 해시값 계산
     */
    private String computeSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192]; // 8KB 버퍼
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 Java 표준 알고리즘이라 실제로 발생하지 않음
            throw new RuntimeException("SHA-256 알고리즘 없음. JVM 환경 확인 필요.", e);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.NEWSLETTER_FILE_READ_FAILED);
        }
    }

    /**
     * 동일 파일 중복 업로드 여부 확인.
     * 중복 판단 기준 (DB Partial Unique Index와 동일)
     *  자녀 특정: (user_id + child_name + file_hash) 조합
     *  자녀 미선택: (user_id + file_hash) 조합
     */
    private void checkDuplicate(Long userId, String childName, String fileHash) {
        boolean isDuplicate;

        if (childName != null) {
            // 자녀 특정: 같은 사용자의 같은 자녀에게 동일 파일이 이미 있는지 확인
            isDuplicate = newsletterRepository
                .findByUserIdAndChildNameAndFileHash(userId, childName, fileHash)
                .isPresent();
        } else {
            // 자녀 미선택: 같은 사용자가 자녀 미선택으로 동일 파일을 이미 올렸는지 확인
            isDuplicate = newsletterRepository
                .findByUserIdAndChildNameIsNullAndFileHash(userId, fileHash)
                .isPresent();
        }

        if (isDuplicate) {
            throw new BusinessException(ErrorCode.NEWSLETTER_DUPLICATE);
        }
    }

    /**
     * newsletterId로 newsletter 레코드 조회.
     */
    private Newsletter findNewsletterById(Long newsletterId) {
        return newsletterRepository.findById(newsletterId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND));
    }

    /**
     * 해당 가정통신문이 현재 사용자 소유인지 검증.
     */
    private void validateOwnership(Newsletter newsletter, Long userId) {
        if (!newsletter.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND);
        }
    }
}
