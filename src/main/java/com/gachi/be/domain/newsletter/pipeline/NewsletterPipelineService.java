package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrField;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.file.config.S3Properties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * 가정통신문 AI 분석 파이프라인 오케스트레이터.
 * 업로드 완료 직후 @Async로 비동기 실행.
 * S3에서 파일 다운로드
 * 이미지 전처리 (EXIF 회전 보정, PDF는 클로바가 직접 처리)
 * 클로바 OCR 호출 (PDF/이미지 모두 지원, 여러 페이지도 1회 호출로 처리)
 * OCR 결과 파싱 (Y좌표 기준 정렬 후 텍스트 합치기)
 * 텍스트 정제 (노이즈 제거)
 * 파파고 번역 (KO이면 스킵)
 * DB 업데이트 (COMPLETED)
 * 예외 발생 시 FAILED로 업데이트하고 종료.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterPipelineService {

    private final NewsletterRepository newsletterRepository;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final ImagePreprocessor imagePreprocessor;
    private final ClovaOcrClient clovaOcrClient;
    private final OcrTextRefiner ocrTextRefiner;
    private final PapagoTranslateClient papagoTranslateClient;

    /**
     * 가정통신문 AI 분석 파이프라인을 비동기로 실행.
     */
    @Async
    @Transactional
    public void runPipeline(Long newsletterId) {
        log.info("[Pipeline] 파이프라인 시작. newsletterId={}", newsletterId);

        Newsletter newsletter = newsletterRepository.findById(newsletterId).orElse(null);
        if (newsletter == null) {
            log.error("[Pipeline] newsletter를 찾을 수 없습니다. newsletterId={}", newsletterId);
            return;
        }

        // PROCESSING 상태로 전이
        newsletter.startProcessing();
        newsletterRepository.save(newsletter);
        log.debug("[Pipeline] PROCESSING 전이 완료. newsletterId={}", newsletterId);

        try {
            // S3에서 파일 다운로드
            log.debug("[Pipeline][STEP1] S3 다운로드 시작. fileKey={}", newsletter.getFileKey());
            byte[] fileBytes = downloadFromS3(newsletter.getFileKey());
            log.debug("[Pipeline][STEP1] 다운로드 완료. size={}bytes", fileBytes.length);

            // 이미지 전처리 (이미지만 해당, PDF는 스킵-> clova가 처리함)
            // 이미지(jpg/png)만 EXIF 회전 보정을 수행한다.
            boolean isPdf = newsletter.getFileKey().toLowerCase().endsWith(".pdf");
            if (!isPdf) {
                log.debug("[Pipeline][STEP2] 이미지 EXIF 회전 보정 시작.");
                fileBytes = imagePreprocessor.preprocessImage(fileBytes);
                log.debug("[Pipeline][STEP2] 전처리 완료. processedSize={}bytes", fileBytes.length);
            } else {
                log.debug("[Pipeline][STEP2] PDF 파일. 전처리 스킵 (클로바가 직접 처리).");
            }

            // 클로바 OCR 호출
            // PDF: format="pdf" → 클로바가 전 페이지 처리 → 모든 pages fields 합쳐서 반환
            // 이미지: format="jpeg"/"png" → 단일 이미지 처리
            log.debug("[Pipeline][STEP3] 클로바 OCR 호출 시작.");
            List<OcrField> ocrFields = clovaOcrClient.callOcr(
                s3Properties.getBucket(), newsletter.getFileKey());
            log.debug("[Pipeline][STEP3] OCR 완료. totalFieldsCount={}", ocrFields.size());

            // OCR 결과 파싱
            log.debug("[Pipeline][STEP4] 텍스트 파싱 시작.");
            String ocrText = ocrTextRefiner.parseFields(ocrFields);
            log.debug("[Pipeline][STEP4] 파싱 완료. length={}chars", ocrText.length());

            // 텍스트 정제
            log.debug("[Pipeline][STEP5] 텍스트 정제 시작.");
            String originalText = ocrTextRefiner.refineText(ocrText);
            log.debug("[Pipeline][STEP5] 정제 완료. length={}chars", originalText.length());

            // 파파고 번역
            log.debug("[Pipeline][STEP6] 번역 시작. language={}", newsletter.getLanguage());
            String translatedText = papagoTranslateClient.translate(
                originalText, newsletter.getLanguage());
            log.debug("[Pipeline][STEP6] 번역 완료. translated={}",
                translatedText != null ? translatedText.length() + "chars" : "null(KO 스킵)");

            // DB 업데이트 (COMPLETED)
            // title, summary는 추후 OpenAI 연동 시 채워질 예정. 현재는 null.
            newsletter.complete(ocrText, originalText, translatedText, null, null);
            newsletterRepository.save(newsletter);

            log.info("[Pipeline] 파이프라인 완료. newsletterId={}", newsletterId);

        } catch (Exception e) {
            log.error("[Pipeline] 파이프라인 실패. newsletterId={}, error={}",
                newsletterId, e.getMessage(), e);
            newsletter.fail();
            newsletterRepository.save(newsletter);
        }
    }

    /**
     * S3에서 파일을 바이트 배열로 다운로드.
     */
    private byte[] downloadFromS3(String fileKey) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(s3Properties.getBucket())
            .key(fileKey)
            .build();

        ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(request);
        return responseBytes.asByteArray();
    }
}
