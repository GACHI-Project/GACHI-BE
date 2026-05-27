package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrField;
import com.gachi.be.domain.newsletter.pipeline.NewsletterAiAnalyzer.AiAnalysisResult;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.file.config.S3Properties;
import com.gachi.be.global.exception.ExternalApiException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
  private final NewsletterDateCandidateService newsletterDateCandidateService;
  private final NewsletterPipelineStatusService newsletterPipelineStatusService;

  @Async
  @Transactional
  public void runPipeline(Long newsletterId) {
    log.info("[Pipeline] 파이프라인 시작. newsletterId={}", newsletterId);

    Newsletter newsletter = newsletterRepository.findById(newsletterId).orElse(null);
    if (newsletter == null) {
      log.error("[Pipeline] newsletter를 찾을 수 없습니다. newsletterId={}", newsletterId);
      return;
    }

    newsletterPipelineStatusService.markProcessing(newsletterId);
    log.debug("[Pipeline] PROCESSING 전환 완료. newsletterId={}", newsletterId);

    String tempFileKey = null;
    String failureStage = "PIPELINE_START";
    String ocrText = null;
    String originalText = null;
    String translatedText = null;

    try {
      failureStage = "S3_DOWNLOAD";
      log.debug("[Pipeline][STEP1] S3 다운로드 시작. fileKey={}", newsletter.getFileKey());
      byte[] fileBytes = downloadFromS3(newsletter.getFileKey());
      log.debug("[Pipeline][STEP1] 다운로드 완료. size={}bytes", fileBytes.length);

      boolean isPdf = newsletter.getFileKey().toLowerCase().endsWith(".pdf");
      String ocrTargetKey;

      failureStage = "IMAGE_PREPROCESS";
      if (isPdf) {
        log.debug("[Pipeline][STEP2] PDF 파일. Clova OCR에 원본 파일을 전달합니다.");
        ocrTargetKey = newsletter.getFileKey();
      } else {
        log.debug("[Pipeline][STEP2] 이미지 EXIF 회전 보정 시작.");
        byte[] processedBytes = imagePreprocessor.preprocessImage(fileBytes);
        tempFileKey = newsletter.getFileKey() + "_processed";
        uploadBytesToS3(processedBytes, tempFileKey, "image/png");
        ocrTargetKey = tempFileKey;
        log.debug("[Pipeline][STEP2] 전처리 파일 임시 업로드 완료. tempFileKey={}", tempFileKey);
      }

      failureStage = "CLOVA_OCR";
      log.debug("[Pipeline][STEP3] Clova OCR 호출 시작. ocrTargetKey={}", ocrTargetKey);
      List<List<OcrField>> ocrPageFields =
          clovaOcrClient.callOcr(s3Properties.getBucket(), ocrTargetKey);
      log.debug("[Pipeline][STEP3] OCR 완료. totalFieldsCount={}", ocrPageFields.size());

      failureStage = "OCR_TEXT_PARSE";
      log.debug("[Pipeline][STEP4] OCR 텍스트 파싱 시작.");
      ocrText = ocrTextRefiner.parseFields(ocrPageFields);
      log.debug("[Pipeline][STEP4] 파싱 완료. length={}chars", ocrText.length());

      failureStage = "TEXT_REFINE";
      log.debug("[Pipeline][STEP5] 텍스트 정제 및 날짜 후보 추출 시작.");
      originalText = ocrTextRefiner.refineText(ocrText);
      newsletterDateCandidateService.extractAndReplace(newsletterId, originalText);
      log.debug("[Pipeline][STEP5] 정제 완료. length={}chars", originalText.length());

      failureStage = "PAPAGO_TRANSLATE";
      log.debug("[Pipeline][STEP6] Papago 번역 시작. language={}", newsletter.getLanguage());
      translatedText = papagoTranslateClient.translate(originalText, newsletter.getLanguage());
      log.debug(
          "[Pipeline][STEP6] 번역 완료. translated={}",
          translatedText != null ? translatedText.length() + "chars" : "null(KO 스킵)");

      failureStage = "AI_SERVER";
      log.debug("[Pipeline][STEP7] AI 서버 분석 시작.");
      AiAnalysisResult aiResult;
      try {
        aiResult =
            newsletterAiAnalyzer.analyze(
                newsletterId, originalText, translatedText, newsletter.getLanguage());
      } catch (ExternalApiException e) {
        log.error(
            "[Pipeline][STEP7] AI 서버 분석 실패. newsletterId={}, stage={}, exceptionType={}, error={}",
            newsletterId,
            failureStage,
            e.getClass().getSimpleName(),
            e.getMessage(),
            e);
        newsletterPipelineStatusService.markFailedWithSnapshot(
            newsletterId, ocrText, originalText, translatedText, failureStage, failureReason(e));
        return;
      }
      log.debug("[Pipeline][STEP7] AI 서버 분석 완료. title={}", aiResult.title());

      newsletterPipelineStatusService.markCompleted(
          newsletterId,
          ocrText,
          originalText,
          translatedText,
          aiResult.title(),
          aiResult.summary());

      log.info("[Pipeline] 파이프라인 완료. newsletterId={}", newsletterId);
    } catch (Exception e) {
      log.error(
          "[Pipeline] 파이프라인 실패. newsletterId={}, stage={}, exceptionType={}, error={}",
          newsletterId,
          failureStage,
          e.getClass().getSimpleName(),
          e.getMessage(),
          e);
      newsletterPipelineStatusService.markFailedWithSnapshot(
          newsletterId, ocrText, originalText, translatedText, failureStage, failureReason(e));
    } finally {
      if (tempFileKey != null) {
        try {
          deleteFromS3(tempFileKey);
          log.debug("[Pipeline] 임시 파일 삭제 완료. tempFileKey={}", tempFileKey);
        } catch (Exception ex) {
          log.warn(
              "[Pipeline] 임시 파일 삭제 실패. tempFileKey={}, error={}", tempFileKey, ex.getMessage());
        }
      }
    }
  }

  private String failureReason(Exception e) {
    String message = e.getMessage();
    if (message == null || message.isBlank()) {
      return e.getClass().getSimpleName();
    }
    return e.getClass().getSimpleName() + ": " + message;
  }

  private byte[] downloadFromS3(String fileKey) {
    GetObjectRequest request =
        GetObjectRequest.builder().bucket(s3Properties.getBucket()).key(fileKey).build();

    ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(request);
    return responseBytes.asByteArray();
  }

  private void uploadBytesToS3(byte[] bytes, String key, String contentType) {
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(s3Properties.getBucket())
            .key(key)
            .contentType(contentType)
            .build();
    s3Client.putObject(request, RequestBody.fromBytes(bytes));
  }

  private void deleteFromS3(String fileKey) {
    s3Client.deleteObject(b -> b.bucket(s3Properties.getBucket()).key(fileKey));
  }
}
