package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.NewsletterPage;
import com.gachi.be.domain.newsletter.entity.NewsletterPageBlock;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPausedReason;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPausedStage;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterSourceType;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.DocumentSource;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrPageResult;
import com.gachi.be.domain.newsletter.pipeline.ImagePreprocessor.PreprocessedImage;
import com.gachi.be.domain.newsletter.pipeline.NewsletterAiAnalyzer.AiAnalysisResult;
import com.gachi.be.domain.newsletter.pipeline.NewsletterPageService.PageOcrText;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.file.config.S3Properties;
import com.gachi.be.global.exception.ExternalApiException;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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

/**
 * 가정통신문 분석 파이프라인.
 *
 * 페이지 단위 파이프라인으로 변경. 흐름은 아래와 같다.
 *
 * [1단계] 페이지 OCR (순차) - 이미지: 장마다 EXIF 보정(회전 시 JPEG 저장) → 클로바 OCR(통신 실패 시 자동 1회 재시도) → 페이지 원문 +
 * 오버레이 블록 저장 · 통신 실패 → 페이지 OCR_FAILED, 문서 PAUSED (다시 시도) · 인식 불가 → 페이지 UNREADABLE, 문서 PAUSED (다시 시도
 * → 이후 건너뛰기) - PDF: 클로바 OCR 1회(자동 1회 재시도) → 페이지별 원문 저장, 인식 못 한 페이지는 자동 건너뛰기 · 통신 실패 → 문서 FAILED
 * (기존처럼 '다시 분석') [2단계] 전체 원문 = 페이지 원문 이어 붙이기 → 중복 검사(content hash) + 날짜 후보 추출 [3단계] 페이지 번역 (순차,
 * Papago만 사용) - 이미지: 블록들을 1회 호출로 번역, 페이지 번역 = 블록 번역 이어 붙이기 - PDF: 페이지 원문 번역 · 실패(자동 1회 재시도 후) → 페이지
 * TRANSLATION_FAILED, 문서 PAUSED (다시 시도 → 이후 다시 시도/건너뛰기) [4단계] 전체 번역 = 페이지 번역 이어 붙이기 → AI 분석 1회 →
 * COMPLETED
 *
 * 이어서 진행/건너뛰기/다시 분석으로 다시 실행되면, 이미 끝난 페이지는 상태를 보고 건너뛰고 멈춘 페이지부터 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterPipelineService {

  private static final String PDF_MIME_TYPE = "application/pdf";
  private static final String JPEG_MIME_TYPE = "image/jpeg";
  private static final String PNG_MIME_TYPE = "image/png";

  /** 회전 보정 JPEG 키 접미사. 원본 키 뒤에 붙여 같은 폴더에 저장한다. */
  private static final String DISPLAY_FILE_SUFFIX = "_display.jpg";

  /** 문서 전체 텍스트를 만들 때 페이지 사이 구분자. (기존 OcrTextRefiner.parseFields와 같은 줄바꿈 1개) */
  private static final String PAGE_SEPARATOR = "\n";

  /** 외부 API 자동 재시도 전 대기 시간. (일시적인 네트워크 오류 회복용) */
  private static final long AUTO_RETRY_DELAY_MILLIS = 1000L;

  private static final String KOREAN_LANGUAGE = "KO";
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
  private final NewsletterPageService newsletterPageService;
  private final OcrBlockGrouper ocrBlockGrouper;
  private final Clock clock;

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

    String failureStage = "PIPELINE_START";
    String ocrText = null;
    String originalText = null;
    String translatedText = null;

    try {
      // 파일 키 목록을 직접 순회하던 방식 → 페이지 레코드 기준으로 처리
      failureStage = "PAGE_PREPARE";
      List<NewsletterPage> pages = newsletterPageService.preparePages(newsletterId);
      NewsletterSourceType sourceType = newsletter.resolveSourceType();
      String language = newsletter.getLanguage();
      log.debug(
          "[Pipeline] 페이지 준비 완료. sourceType={}, pages={}, newsletterId={}",
          sourceType,
          pages.size(),
          newsletterId);

      // 1단계: 페이지 OCR
      if (sourceType == NewsletterSourceType.PDF) {
        if (pages.isEmpty()) {
          // PDF는 OCR을 아직 안 했을 때만 호출한다. (이어서 진행/다시 분석 시에는 저장된 페이지 OCR 결과 재사용)
          failureStage = "CLOVA_OCR";
          pages = runPdfOcr(newsletterId, newsletter.resolveFileKeys().get(0));
        }
      } else {
        for (NewsletterPage page : pages) {
          if (!page.getStatus().needsOcr()) {
            continue;
          }
          failureStage = "IMAGE_PAGE_OCR";
          if (!runImagePageOcr(newsletterId, page, pages.size())) {
            return; // PAUSED 전환됨. 사용자가 이어서 진행/건너뛰기를 누르면 이 페이지부터 다시 시작한다.
          }
        }
        pages = newsletterPageService.findPages(newsletterId);
      }
      log.debug("[Pipeline][STEP3] 전체 페이지 OCR 완료. pages={}", pages.size());

      // 2단계: 전체 원문 → 중복 검사 + 날짜 후보
      failureStage = "OCR_TEXT_PARSE";
      ocrText = joinPageTexts(pages, NewsletterPage::getOcrText);
      originalText = joinPageTexts(pages, NewsletterPage::getOriginalText);
      if (originalText == null) {
        log.warn("[Pipeline] 모든 페이지에서 글자를 인식하지 못했습니다. newsletterId={}", newsletterId);
        newsletterPipelineStatusService.markFailedWithSnapshot(
            newsletterId, null, null, null, "NO_RECOGNIZED_PAGE", "모든 페이지에서 글자를 인식하지 못했습니다.");
        return;
      }
      log.debug("[Pipeline][STEP4] 전체 원문 생성 완료. length={}chars", originalText.length());

      failureStage = "TEXT_REFINE";
      // 정제는 페이지 OCR 단계에서 끝나므로 중복 검사/날짜 후보 추출만 표시
      log.debug("[Pipeline][STEP5] 중복 검사 및 날짜 후보 추출 시작.");
      String contentHash = newsletterContentHasher.hash(originalText).orElse(null);
      if (newsletterPipelineStatusService.markFailedIfContentDuplicated(
          newsletterId, ocrText, originalText, contentHash)) {
        log.info("[Pipeline] 본문 중복 가정통신문으로 분석을 중단합니다. newsletterId={}", newsletterId);
        return;
      }
      newsletterDateCandidateService.extractAndReplace(newsletterId, originalText);
      log.debug("[Pipeline][STEP5] 완료.");

      // 3단계: 페이지 번역
      // 문서 전체 1회 번역 → 페이지별 순차 번역 (실패 시 해당 페이지에서 멈춤)
      failureStage = "PAPAGO_TRANSLATE";
      log.debug("[Pipeline][STEP6] 페이지별 Papago 번역 시작. language={}", language);
      for (NewsletterPage page : pages) {
        if (!page.getStatus().needsTranslation()) {
          continue;
        }
        if (!translatePage(newsletterId, page, language, ocrText, originalText)) {
          return; // PAUSED 전환됨
        }
      }
      pages = newsletterPageService.findPages(newsletterId);
      translatedText =
          KOREAN_LANGUAGE.equals(language)
              ? null
              : joinPageTexts(pages, NewsletterPage::getTranslatedText);
      log.debug(
          "[Pipeline][STEP6] 번역 완료. translated={}",
          translatedText != null ? translatedText.length() + "chars" : "null(KO 스킵)");

      // 4단계: AI 분석 (모든 페이지가 정리된 뒤 1회)
      failureStage = "AI_SERVER";
      log.debug("[Pipeline][STEP7] AI 서버 분석 시작.");
      // AI 원본 첨부 목록을 페이지 레코드 기준으로 만든다. (기존: OCR 루프에서 임시 PNG 키를 누적)
      //   newsletter.getLanguage() 대신 위에서 꺼낸 language 변수를 사용한다.
      List<DocumentSource> aiDocuments = buildAiDocuments(sourceType, pages);
      AiAnalysisResult aiResult;
      try {
        aiResult =
            newsletterAiAnalyzer.analyze(
                newsletterId, originalText, translatedText, language, aiDocuments);
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
    }
  }

  // 페이지 단위 처리
  /**
   * PDF 전체를 클로바 OCR 1회로 처리하고 페이지 레코드를 만든다.
   *
   * 클로바 호출 자체가 (자동 재시도 후에도) 실패하면 예외를 그대로 던져 문서를 FAILED로 만든다. PDF는 페이지 하나만 다시 OCR할 수 없어서
   * 멈춤(PAUSED) 대신 기존 '다시 분석' 흐름을 쓰기로 했다.
   */
  private List<NewsletterPage> runPdfOcr(Long newsletterId, String pdfFileKey) {
    log.debug("[Pipeline][STEP3] PDF Clova OCR 호출 시작. fileKey={}", pdfFileKey);
    List<OcrPageResult> results =
        callWithAutoRetry(
            "PDF OCR", () -> clovaOcrClient.callOcrPages(s3Properties.getBucket(), pdfFileKey));

    List<PageOcrText> pageTexts = new ArrayList<>();
    for (OcrPageResult result : results) {
      String pageOcrText = result.recognized() ? parsePageText(result) : null;
      String pageOriginalText = pageOcrText == null ? null : ocrTextRefiner.refineText(pageOcrText);
      pageTexts.add(new PageOcrText(result.pageIndex() + 1, pageOcrText, pageOriginalText));
    }
    log.debug("[Pipeline][STEP3] PDF OCR 완료. totalPages={}", pageTexts.size());
    return newsletterPageService.createPdfPages(newsletterId, pdfFileKey, pageTexts);
  }

  /**
   * 이미지 1장을 OCR한다.
   *
   * @return 성공하면 true. 실패해서 문서를 PAUSED로 멈췄으면 false
   */
  private boolean runImagePageOcr(Long newsletterId, NewsletterPage page, int totalPages)
      throws IOException {
    int pageNo = page.getPageNo();
    NewsletterPage current = page;

    // STEP1~2: 표시/OCR용 이미지 준비. 이미 준비된 페이지(이어서 진행)는 다시 만들지 않는다.
    if (current.getDisplayFileKey() == null) {
      log.debug(
          "[Pipeline][STEP1] S3 다운로드 시작. page={}/{}, fileKey={}",
          pageNo,
          totalPages,
          page.getFileKey());
      byte[] fileBytes = downloadFromS3(page.getFileKey());

      log.debug("[Pipeline][STEP2] 이미지 EXIF 회전 보정 시작. page={}", pageNo);
      PreprocessedImage preprocessed = imagePreprocessor.preprocessForDisplay(fileBytes);
      String displayFileKey = page.getFileKey();
      if (preprocessed.rotated()) {
        displayFileKey = page.getFileKey() + DISPLAY_FILE_SUFFIX;
        uploadBytesToS3(preprocessed.jpegBytes(), displayFileKey, JPEG_MIME_TYPE);
        log.debug("[Pipeline][STEP2] 회전 보정 JPEG 저장 완료. displayFileKey={}", displayFileKey);
      }
      current =
          newsletterPageService.updateDisplayImage(
              page.getId(), displayFileKey, preprocessed.width(), preprocessed.height());
    }

    // STEP3: 클로바 OCR (통신 실패 시 자동 1회 재시도)
    String ocrTargetKey = current.getDisplayFileKey();
    log.debug(
        "[Pipeline][STEP3] Clova OCR 호출 시작. page={}/{}, key={}", pageNo, totalPages, ocrTargetKey);
    List<OcrPageResult> results;
    try {
      results =
          callWithAutoRetry(
              "이미지 OCR", () -> clovaOcrClient.callOcrPages(s3Properties.getBucket(), ocrTargetKey));
    } catch (ExternalApiException e) {
      log.warn(
          "[Pipeline][STEP3] OCR 통신 실패로 멈춥니다. newsletterId={}, page={}, error={}",
          newsletterId,
          pageNo,
          e.getMessage());
      newsletterPageService.markOcrFailed(page.getId());
      pause(newsletterId, pageNo, NewsletterPausedStage.OCR, NewsletterPausedReason.OCR_FAILED);
      return false;
    }

    OcrPageResult result = results.isEmpty() ? null : results.get(0);
    String pageOcrText = result != null && result.recognized() ? parsePageText(result) : "";
    String pageOriginalText = ocrTextRefiner.refineText(pageOcrText);
    if (pageOriginalText.isBlank()) {
      log.warn(
          "[Pipeline][STEP3] 글자를 인식하지 못해 멈춥니다. newsletterId={}, page={}", newsletterId, pageNo);
      newsletterPageService.markUnreadable(page.getId());
      pause(newsletterId, pageNo, NewsletterPausedStage.OCR, NewsletterPausedReason.UNREADABLE);
      return false;
    }

    // 오버레이 블록: 좌표 기준 이미지 = OCR에 사용한 이미지(display) → 0~1 비율로 저장
    List<NewsletterPageBlock> blocks =
        buildBlocks(result, current.getImageWidth(), current.getImageHeight());
    newsletterPageService.completeOcr(page.getId(), pageOcrText, pageOriginalText, blocks);
    log.debug(
        "[Pipeline][STEP3] OCR 완료. page={}/{}, length={}chars, blocks={}",
        pageNo,
        totalPages,
        pageOriginalText.length(),
        blocks.size());
    return true;
  }

  /**
   * 페이지 1장을 번역한다. 한국어 사용자는 번역 없이 완료 처리한다.
   *
   * @return 성공하면 true. 실패해서 문서를 PAUSED로 멈췄으면 false
   */
  private boolean translatePage(
      Long newsletterId,
      NewsletterPage page,
      String language,
      String documentOcrText,
      String documentOriginalText) {
    if (KOREAN_LANGUAGE.equals(language)) {
      newsletterPageService.completeTranslation(page.getId(), null, null);
      return true;
    }

    try {
      List<NewsletterPageBlock> blocks = page.getBlocks();
      if (blocks != null && !blocks.isEmpty()) {
        // 이미지: 블록 번역 → 페이지 번역 = 블록 번역을 줄바꿈으로 이어 붙인 것
        List<String> blockTexts = blocks.stream().map(NewsletterPageBlock::originalText).toList();
        List<String> translatedBlocks =
            callWithAutoRetry(
                "블록 번역", () -> papagoTranslateClient.translateAll(blockTexts, language));
        List<NewsletterPageBlock> translated = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
          translated.add(blocks.get(i).withTranslatedText(translatedBlocks.get(i)));
        }
        newsletterPageService.completeTranslation(
            page.getId(), String.join(PAGE_SEPARATOR, translatedBlocks), translated);
      } else {
        // PDF: 페이지 원문 번역
        String translatedText =
            callWithAutoRetry(
                "페이지 번역", () -> papagoTranslateClient.translate(page.getOriginalText(), language));
        newsletterPageService.completeTranslation(page.getId(), translatedText, null);
      }
      log.debug("[Pipeline][STEP6] 페이지 번역 완료. page={}", page.getPageNo());
      return true;
    } catch (ExternalApiException e) {
      log.warn(
          "[Pipeline][STEP6] 번역 실패로 멈춥니다. newsletterId={}, page={}, error={}",
          newsletterId,
          page.getPageNo(),
          e.getMessage());
      newsletterPageService.markTranslationFailed(page.getId());
      // 번역 단계에서 멈추면 원문은 이미 있으므로, 24시간 후 FAILED가 되어도 원문을 볼 수 있게 스냅샷을 함께 저장한다.
      pause(
          newsletterId,
          page.getPageNo(),
          NewsletterPausedStage.TRANSLATION,
          NewsletterPausedReason.TRANSLATION_FAILED,
          documentOcrText,
          documentOriginalText);
      return false;
    }
  }

  /** OCR 결과 1페이지를 줄 단위 텍스트로 파싱한다. (기존 파서를 그대로 사용해 AI 입력 형태를 유지) */
  private String parsePageText(OcrPageResult result) {
    return ocrTextRefiner.parseFields(List.of(result.fields()));
  }

  /** 오버레이 블록을 만들고, 블록 원문도 페이지 원문과 같은 규칙으로 정제한다. 정제 후 빈 블록은 버리고 번호를 다시 매긴다. */
  private List<NewsletterPageBlock> buildBlocks(OcrPageResult result, int width, int height) {
    List<NewsletterPageBlock> grouped = ocrBlockGrouper.group(result.fields(), width, height);
    List<NewsletterPageBlock> blocks = new ArrayList<>();
    for (NewsletterPageBlock block : grouped) {
      String refined = ocrTextRefiner.refineText(block.originalText());
      if (refined.isBlank()) {
        continue;
      }
      blocks.add(new NewsletterPageBlock(blocks.size() + 1, refined, null, block.box()));
    }
    return blocks;
  }

  /**
   * AI 서버에 원본으로 첨부할 문서 목록. PDF는 PDF 1개, 이미지는 OCR에 사용한 이미지(회전 보정 JPEG 또는 원본)를 페이지 순서대로 넣는다. 글자를 인식하지
   * 못해 건너뛴 페이지는 AI가 흐린 이미지에서 잘못된 내용을 읽지 않도록 제외한다.
   */
  private List<DocumentSource> buildAiDocuments(
      NewsletterSourceType sourceType, List<NewsletterPage> pages) {
    if (sourceType == NewsletterSourceType.PDF) {
      return pages.isEmpty()
          ? List.of()
          : List.of(new DocumentSource(pages.get(0).getFileKey(), PDF_MIME_TYPE));
    }
    List<DocumentSource> documents = new ArrayList<>();
    for (NewsletterPage page : pages) {
      if (page.getOriginalText() == null || page.getOriginalText().isBlank()) {
        continue;
      }
      String key = page.getDisplayFileKey() != null ? page.getDisplayFileKey() : page.getFileKey();
      String mimeType = key.toLowerCase().endsWith(".png") ? PNG_MIME_TYPE : JPEG_MIME_TYPE;
      documents.add(new DocumentSource(key, mimeType));
    }
    return documents;
  }

  /** 페이지 텍스트를 순서대로 이어 붙인다. 이어 붙일 내용이 없으면 null. */
  private String joinPageTexts(
      List<NewsletterPage> pages, Function<NewsletterPage, String> getter) {
    String joined =
        pages.stream()
            .map(getter)
            .filter(Objects::nonNull)
            .filter(text -> !text.isBlank())
            .collect(Collectors.joining(PAGE_SEPARATOR));
    return joined.isBlank() ? null : joined;
  }

  /** 외부 API 호출을 실패 시 1회 자동 재시도한다. 재시도도 실패하면 예외를 그대로 던진다. */
  private <T> T callWithAutoRetry(String label, Supplier<T> action) {
    try {
      return action.get();
    } catch (ExternalApiException first) {
      log.warn(
          "[Pipeline] {} 실패. {}ms 후 자동 재시도합니다. error={}",
          label,
          AUTO_RETRY_DELAY_MILLIS,
          first.getMessage());
      sleepBeforeRetry();
      return action.get();
    }
  }

  private void sleepBeforeRetry() {
    try {
      Thread.sleep(AUTO_RETRY_DELAY_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void pause(
      Long newsletterId, int pageNo, NewsletterPausedStage stage, NewsletterPausedReason reason) {
    pause(newsletterId, pageNo, stage, reason, null, null);
  }

  private void pause(
      Long newsletterId,
      int pageNo,
      NewsletterPausedStage stage,
      NewsletterPausedReason reason,
      String ocrText,
      String originalText) {
    boolean paused =
        newsletterPipelineStatusService.markPaused(
            newsletterId, pageNo, stage, reason, OffsetDateTime.now(clock), ocrText, originalText);
    log.info(
        "[Pipeline] 파이프라인 멈춤. newsletterId={}, page={}, stage={}, reason={}, paused={}",
        newsletterId,
        pageNo,
        stage,
        reason,
        paused);
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
}
