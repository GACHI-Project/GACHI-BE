package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrField;
import com.gachi.be.domain.newsletter.pipeline.NewsletterAiAnalyzer.AiAnalysisResult;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.file.config.S3Properties;
import com.gachi.be.global.exception.ExternalApiException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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
  private final NewsletterContentHasher newsletterContentHasher;
  private final NewsletterCulturalGuideService newsletterCulturalGuideService;

  @Async
  public void runPipeline(Long newsletterId) {
    log.info("[Pipeline] 파이프라인 시작. newsletterId={}", newsletterId);

    Newsletter newsletter = newsletterRepository.findById(newsletterId).orElse(null);
    if (newsletter == null) {
      log.error("[Pipeline] newsletter를 찾을 수 없습니다. newsletterId={}", newsletterId);
      return;
    }

    newsletterPipelineStatusService.markProcessing(newsletterId);
    log.debug("[Pipeline] PROCESSING 전환 완료. newsletterId={}", newsletterId);

    List<String> tempFileKeys = new ArrayList<>();
    String failureStage = "PIPELINE_START";
    String ocrText = null;
    String originalText = null;
    String translatedText = null;

    try {
      // 페이지 파일 키 목록 조회.
      // file_keys(JSONB)가 비어있는 과거 데이터는 file_key 단건으로 대체되므로 기존 문서도 그대로 동작한다.
      List<String> fileKeys = newsletter.resolveFileKeys();
      if (fileKeys.isEmpty()) {
        throw new IllegalStateException("OCR 대상 파일 키가 없습니다. newsletterId=" + newsletterId);
      }
      log.debug("[Pipeline] OCR 대상 페이지 수={}. newsletterId={}", fileKeys.size(), newsletterId);

      // STEP1~3을 페이지 단위 루프로 처리.
      // 페이지 순서를 보장해야 하므로 순차 호출한다. (병렬 호출은 외부 API rate limit·부분 실패 처리가 복잡해짐)
      List<List<OcrField>> ocrPageFields = new ArrayList<>();

      for (int pageIndex = 0; pageIndex < fileKeys.size(); pageIndex++) {
        String fileKey = fileKeys.get(pageIndex);

        failureStage = "S3_DOWNLOAD";
        log.debug(
            "[Pipeline][STEP1] S3 다운로드 시작. page={}/{}, fileKey={}",
            pageIndex + 1,
            fileKeys.size(),
            fileKey);
        byte[] fileBytes = downloadFromS3(fileKey);
        log.debug("[Pipeline][STEP1] 다운로드 완료. size={}bytes", fileBytes.length);

        // newsletter.getFileKey()(대표 키)가 아니라 현재 페이지의 fileKey를 기준으로 판단해야 한다.
        boolean isPdf = fileKey.toLowerCase().endsWith(".pdf");
        String ocrTargetKey;

        failureStage = "IMAGE_PREPROCESS";
        if (isPdf) {
          log.debug("[Pipeline][STEP2] PDF 파일. Clova OCR에 원본 파일을 전달합니다.");
          // 대표 키가 아닌 현재 페이지 키를 OCR 대상으로 사용
          ocrTargetKey = fileKey;
        } else {
          log.debug("[Pipeline][STEP2] 이미지 EXIF 회전 보정 시작. page={}", pageIndex + 1);
          byte[] processedBytes = imagePreprocessor.preprocessImage(fileBytes);
          // tempFileKey를 루프 지역 변수로 선언하고, 페이지 키 기준으로 임시 키를 만든다.
          String tempFileKey = fileKey + "_processed";
          uploadBytesToS3(processedBytes, tempFileKey, "image/png");
          // finally에서 정리할 수 있도록 임시 키를 목록에 누적한다. (누락 시 S3에 고아 파일이 남음)
          tempFileKeys.add(tempFileKey);
          ocrTargetKey = tempFileKey;
          log.debug("[Pipeline][STEP2] 전처리 파일 임시 업로드 완료. tempFileKey={}", tempFileKey);
        }

        failureStage = "CLOVA_OCR";
        log.debug("[Pipeline][STEP3] Clova OCR 호출 시작. ocrTargetKey={}", ocrTargetKey);
        // 루프 밖의 ocrPageFields를 재선언하지 않고, 페이지 결과를 순서대로 누적한다.
        // PDF는 클로바가 내부적으로 페이지를 나눠 반환하므로 여러 개가 들어올 수 있고,
        // 이미지는 장당 1개가 들어온다. 두 경우 모두 누적 순서가 문서 페이지 순서가 된다.
        List<List<OcrField>> pageResult =
            clovaOcrClient.callOcr(s3Properties.getBucket(), ocrTargetKey);
        ocrPageFields.addAll(pageResult);
        log.debug(
            "[Pipeline][STEP3] OCR 완료. page={}/{}, 인식 페이지 수={}",
            pageIndex + 1,
            fileKeys.size(),
            pageResult.size());
      }
      // 여기서 페이지 루프를 닫는다. STEP4 이후는 전체 페이지를 합친 뒤 1회만 실행되어야 한다.

      log.debug("[Pipeline][STEP3] 전체 OCR 완료. totalOcrPages={}", ocrPageFields.size());
      failureStage = "OCR_TEXT_PARSE";
      log.debug("[Pipeline][STEP4] OCR 텍스트 파싱 시작.");
      ocrText = ocrTextRefiner.parseFields(ocrPageFields);
      log.debug("[Pipeline][STEP4] 파싱 완료. length={}chars", ocrText.length());

      failureStage = "TEXT_REFINE";
      log.debug("[Pipeline][STEP5] 텍스트 정제 및 날짜 후보 추출 시작.");
      originalText = ocrTextRefiner.refineText(ocrText);
      String contentHash = newsletterContentHasher.hash(originalText).orElse(null);
      if (newsletterPipelineStatusService.markFailedIfContentDuplicated(
          newsletterId, ocrText, originalText, contentHash)) {
        log.info("[Pipeline] 본문 중복 가정통신문으로 분석을 중단합니다. newsletterId={}", newsletterId);
        return;
      }
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
          aiResult.titleI18n(),
          aiResult.summary());

      // STEP8: 문화 맥락 안내(FAQ) 선정.
      // markCompleted 이후에 실행하며, 실패하더라도 파이프라인 전체를 실패로 만들지 않는다.
      // (문화 맥락은 부가 정보이고, 캘린더 preview 저장 실패 처리와 동일한 정책)
      failureStage = "CULTURAL_GUIDE";
      try {
        log.debug("[Pipeline][STEP8] 문화 맥락 안내 선정 시작.");
        newsletterCulturalGuideService.extractAndReplace(newsletterId, originalText);
        log.debug("[Pipeline][STEP8] 문화 맥락 안내 선정 완료.");
      } catch (Exception e) {
        log.warn(
            "[Pipeline][STEP8] 문화 맥락 안내 선정 실패. 분석 결과는 그대로 유지합니다. newsletterId={}, error={}",
            newsletterId,
            e.getMessage(),
            e);
      }

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
      for (String tempFileKey : tempFileKeys) {
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
