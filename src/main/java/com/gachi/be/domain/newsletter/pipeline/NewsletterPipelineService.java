package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrField;
import com.gachi.be.domain.newsletter.pipeline.NewsletterAiAnalyzer.AiAnalysisResult;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.file.config.S3Properties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 가정통신문 AI 분석 파이프라인 오케스트레이터. 업로드 완료 직후 @Async로 비동기 실행. S3에서 파일 다운로드 이미지 전처리 (EXIF 회전 보정, PDF는 클로바가
 * 직접 처리) 클로바 OCR 호출 (PDF/이미지 모두 지원, 여러 페이지도 1회 호출로 처리) OCR 결과 파싱 (Y좌표 기준 정렬 후 텍스트 합치기) 텍스트 정제 (노이즈
 * 제거) 파파고 번역 (KO이면 스킵) OpenAI 분석(제목, 요약, 체크리스트, 해야 할 일) DB 업데이트 (COMPLETED) 예외 발생 시 FAILED로 업데이트하고
 * 종료.
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
  private final NewsletterAiAnalyzer newsletterAiAnalyzer;

  /** 가정통신문 AI 분석 파이프라인을 비동기로 실행. */
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
    markProcessing(newsletterId);
    log.debug("[Pipeline] PROCESSING 전이 완료. newsletterId={}", newsletterId);

    String tempFileKey = null;

    try {
      // S3에서 파일 다운로드
      log.debug("[Pipeline][STEP1] S3 다운로드 시작. fileKey={}", newsletter.getFileKey());
      byte[] fileBytes = downloadFromS3(newsletter.getFileKey());
      log.debug("[Pipeline][STEP1] 다운로드 완료. size={}bytes", fileBytes.length);

      // 이미지 전처리 (이미지만 해당, PDF는 스킵-> clova가 처리함)
      // 이미지(jpg/png)만 EXIF 회전 보정을 수행한다.
      boolean isPdf = newsletter.getFileKey().toLowerCase().endsWith(".pdf");
      String ocrTargetKey;

      if (!isPdf) {
        log.debug("[Pipeline][STEP2] 이미지 EXIF 회전 보정 시작.");
        byte[] processedBytes = imagePreprocessor.preprocessImage(fileBytes);
        log.debug("[Pipeline][STEP2] 전처리 완료. processedSize={}bytes", processedBytes.length);

        // 전처리된 바이트를 임시 S3 키로 업로드
        // 원본 키 + "_processed" 접미사로 임시 키 생성
        tempFileKey = newsletter.getFileKey() + "_processed";
        uploadBytesToS3(processedBytes, tempFileKey, "image/png");
        ocrTargetKey = tempFileKey;
        log.debug("[Pipeline][STEP2] 전처리 파일 임시 업로드 완료. tempFileKey={}", tempFileKey);
      } else {
        log.debug("[Pipeline][STEP2] PDF 파일. 전처리 스킵 (클로바가 직접 처리).");
        ocrTargetKey = newsletter.getFileKey();
      }

      // 클로바 OCR 호출
      // PDF: format="pdf" → 클로바가 전 페이지 처리 → 모든 pages fields 합쳐서 반환
      // 이미지: format="jpeg"/"png" → 단일 이미지 처리
      log.debug("[Pipeline][STEP3] 클로바 OCR 호출 시작. ocrTargetKey={}", ocrTargetKey);
      List<List<OcrField>> ocrPageFields =
          clovaOcrClient.callOcr(s3Properties.getBucket(), ocrTargetKey);
      log.debug("[Pipeline][STEP3] OCR 완료. totalFieldsCount={}", ocrPageFields.size());

      // OCR 결과 파싱
      log.debug("[Pipeline][STEP4] 텍스트 파싱 시작.");
      String ocrText = ocrTextRefiner.parseFields(ocrPageFields);
      log.debug("[Pipeline][STEP4] 파싱 완료. length={}chars", ocrText.length());

      // 텍스트 정제
      log.debug("[Pipeline][STEP5] 텍스트 정제 시작.");
      String originalText = ocrTextRefiner.refineText(ocrText);
      log.debug("[Pipeline][STEP5] 정제 완료. length={}chars", originalText.length());

      // 파파고 번역
      log.debug("[Pipeline][STEP6] 번역 시작. language={}", newsletter.getLanguage());
      String translatedText =
          papagoTranslateClient.translate(originalText, newsletter.getLanguage());
      log.debug(
          "[Pipeline][STEP6] 번역 완료. translated={}",
          translatedText != null ? translatedText.length() + "chars" : "null(KO 스킵)");

      // OpenAI 분석 (제목/요약/체크리스트/해야할일)
      log.debug("[Pipeline][STEP7] OpenAI 분석 시작.");
      AiAnalysisResult aiResult =
          newsletterAiAnalyzer.analyze(
              newsletterId, originalText, translatedText, newsletter.getLanguage());
      log.debug("[Pipeline][STEP7] 완료. title={}", aiResult.title());

      // DB 업데이트 (COMPLETED)
      markCompleted(
          newsletterId,
          ocrText,
          originalText,
          translatedText,
          aiResult.title(),
          aiResult.summary());

      log.info("[Pipeline] 파이프라인 완료. newsletterId={}", newsletterId);

    } catch (Exception e) {
      log.error("[Pipeline] 파이프라인 실패. newsletterId={}, error={}", newsletterId, e.getMessage(), e);
      markFailed(newsletterId);
    } finally {
        if (tempFileKey != null) {
            try {
                deleteFromS3(tempFileKey);
                log.debug("[Pipeline] 임시 파일 삭제 완료. tempFileKey={}", tempFileKey);
            } catch (Exception ex) {
                // 임시 파일 삭제 실패는 파이프라인 결과에 영향 없음 — 로그만 남김
                log.warn("[Pipeline] 임시 파일 삭제 실패. tempFileKey={}, error={}",
                    tempFileKey, ex.getMessage());
            }
        }
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markProcessing(Long newsletterId) {
    newsletterRepository
        .findById(newsletterId)
        .ifPresent(
            n -> {
              n.startProcessing();
              newsletterRepository.save(n);
            });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markCompleted(
      Long newsletterId,
      String ocrText,
      String originalText,
      String translatedText,
      String title,
      String summary) {
    newsletterRepository
        .findById(newsletterId)
        .ifPresent(
            n -> {
              n.complete(ocrText, originalText, translatedText, title, summary);
              newsletterRepository.save(n);
            });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(Long newsletterId) {
    newsletterRepository
        .findById(newsletterId)
        .ifPresent(
            (n -> {
              n.fail();
              newsletterRepository.save(n);
            }));
  }

  /** S3에서 파일을 바이트 배열로 다운로드. */
  private byte[] downloadFromS3(String fileKey) {
    GetObjectRequest request =
        GetObjectRequest.builder().bucket(s3Properties.getBucket()).key(fileKey).build();

    ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(request);
    return responseBytes.asByteArray();
  }

    /** 전처리된 이미지를 임시 키로 저장할 때 사용.*/
    private void uploadBytesToS3(byte[] bytes, String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(s3Properties.getBucket())
            .key(key)
            .contentType(contentType)
            .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
    }

    /**전처리 임시 파일 정리에 사용*/
    private void deleteFromS3(String fileKey) {
        s3Client.deleteObject(b -> b.bucket(s3Properties.getBucket()).key(fileKey));
    }
}
