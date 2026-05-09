package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrField;
import com.gachi.be.domain.newsletter.pipeline.NewsletterAiAnalyzer.AiAnalysisResult;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.file.config.S3Properties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
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
 * 가정통신문 AI 분석 파이프라인 오케스트레이터. 업로드 완료 직후 @Async로 비동기 실행. 파이프라인 흐름: STEP1. S3에서 파일 다운로드 STEP2. 이미지 전처리
 * (EXIF 회전 보정, PDF는 클로바가 직접 처리) STEP2-PDF. PDF를 페이지별 이미지로 변환 → Base64 인코딩 (메모리 처리) STEP3. 클로바 OCR
 * 호출 (PDF/이미지 모두 지원, 여러 페이지도 1회 호출로 처리) STEP4. OCR 결과 파싱 (Y좌표 기준 정렬 후 텍스트 합치기) STEP5. 텍스트 정제 (노이즈
 * 제거) STEP6. 파파고 번역 (KO이면 스킵) STEP7. OpenAI 분석 (제목/요약/체크리스트/해야할일) - 이미지 파일: S3 Presigned URL로
 * Vision 전달 - PDF 파일: STEP2-PDF에서 변환한 Base64 이미지 목록으로 Vision 전달 STEP8. DB 업데이트 (COMPLETED) 예외 발생 시
 * FAILED로 업데이트하고 종료.
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
  // PDF를 이미지로 변환할 때 사용하는 해상도 -> TODO: 현재는 150 DPI인데 더 부족하거나 과하다 싶으면 줄이거나 늘이기: 300DPI는 토큰 4배 증가 위험
  private static final float PDF_RENDER_DPI = 150f;
  // PDF 변환시 처리할 최대 페이지 수
  private static final int PDF_MAX_PAGES = 5;
  // PDF 이미지 총량 제한 -> 이미지 제한을 안하면 timeout 또는 413 에러 발생 가능
  private static final int PDF_MAX_TOTAL_JPEG_BYTES = 8 * 1024 * 1024;

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

    // 이미지 전처리용 임시 S3 키-> 이미지 파일에서만 사용할 거고 추후 finally에서 삭제할거임
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
      // PDF-> 이미지 변환: s3에 저장하지 않고 메모리로만 처리.
      List<String> pdfPageBase64Images = null;
      if (isPdf) {
        log.debug("[Pipeline][STEP2-PDF] PDF 페이지 이미지 변환 시작. maxPages={}", PDF_MAX_PAGES);
        pdfPageBase64Images = convertPdfToBase64Images(fileBytes);
        log.debug(
            "[Pipeline][STEP2-PDF] PDF 이미지 변환 완료. convertedPages={}", pdfPageBase64Images.size());
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
              newsletterId,
              originalText,
              translatedText,
              newsletter.getLanguage(),
              pdfPageBase64Images);
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
          log.warn(
              "[Pipeline] 임시 파일 삭제 실패. tempFileKey={}, error={}", tempFileKey, ex.getMessage());
        }
      }
    }
  }

  private List<String> convertPdfToBase64Images(byte[] pdfBytes) {
    List<String> base64Images = new ArrayList<>();

    // 누적 JPEG 바이트 추적 변수
    int totalJpegBytes = 0;

    // PDFBox로 PDF 파일을 열고 페이지별로 이미지 렌더링
    // try-with-resources: PDDocument는 Closeable이므로 자동으로 닫힘
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      PDFRenderer renderer = new PDFRenderer(document);

      int totalPages = document.getNumberOfPages();
      // 최대 처리 페이지 수를 PDF_MAX_PAGES로 제한
      // 초과 페이지는 OCR 텍스트(originalText)로 이미 커버되어 있음
      int pagesToProcess = Math.min(totalPages, PDF_MAX_PAGES);

      log.debug("[Pipeline] PDF 페이지 렌더링. totalPages={}, processing={}", totalPages, pagesToProcess);

      for (int pageIndex = 0; pageIndex < pagesToProcess; pageIndex++) {
        // DPI 설정으로 페이지를 BufferedImage로 렌더링
        // ImageType.RGB: JPEG는 투명도(ARGB)를 지원하지 않으므로 RGB 사용
        BufferedImage pageImage =
            renderer.renderImageWithDPI(pageIndex, PDF_RENDER_DPI, ImageType.RGB);

        // BufferedImage → JPEG 바이트 배열로 변환
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean written = ImageIO.write(pageImage, "JPEG", baos);

        if (!written) {
          // JPEG 포맷 write 실패 시 해당 페이지만 스킵하고 계속 진행
          log.warn("[Pipeline] 페이지 JPEG 변환 실패. pageIndex={}", pageIndex);
          continue;
        }

        // 이 페이지를 추가했을 때 총량이 제한을 초과하면 중단
        byte[] jpegBytes = baos.toByteArray();
        if (totalJpegBytes + jpegBytes.length > PDF_MAX_TOTAL_JPEG_BYTES) {
          log.warn(
              "[Pipeline] PDF 이미지 총량 제한 도달. pageIndex={}, totalBytes={}. 이후 페이지는 OCR 텍스트로 커버.",
              pageIndex,
              totalJpegBytes);
          break;
        }

        // JPEG 바이트를 Base64 문자열로 인코딩
        String base64 = Base64.getEncoder().encodeToString(jpegBytes);
        base64Images.add(base64);
        totalJpegBytes += jpegBytes.length;

        log.debug("[Pipeline] 페이지 변환 완료. pageIndex={}, jpegBytes={}", pageIndex, baos.size());
      }

    } catch (Exception e) {
      // PDF 변환 실패 시 빈 목록 반환 → OpenAI에 텍스트만 전달하는 fallback으로 동작
      // 파이프라인 전체가 중단되지 않도록 예외를 삼킴
      log.warn("[Pipeline] PDF 이미지 변환 실패. 텍스트만으로 OpenAI 분석 진행. error={}", e.getMessage());
    }

    return base64Images;
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

  /** 전처리된 이미지를 임시 키로 저장할 때 사용. */
  private void uploadBytesToS3(byte[] bytes, String key, String contentType) {
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(s3Properties.getBucket())
            .key(key)
            .contentType(contentType)
            .build();
    s3Client.putObject(request, RequestBody.fromBytes(bytes));
  }

  /** 전처리 임시 파일 정리에 사용 */
  private void deleteFromS3(String fileKey) {
    s3Client.deleteObject(b -> b.bucket(s3Properties.getBucket()).key(fileKey));
  }
}
