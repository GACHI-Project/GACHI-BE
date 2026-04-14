package com.gachi.be.domain.newsletter.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.config.external.ClovaOcrProperties;
import com.gachi.be.global.exception.ExternalApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * 클로바 OCR API 호출 클라이언트.
 *
 * <p>호출 방식: Presigned URL 방식 파일을 Base64로 인코딩하는 대신 S3 Presigned URL을 클로바에 전달하고 클로바가 직접 S3에서 파일을 가져가는
 * 방식.
 *
 * <p>PDF 지원: 클로바 OCR은 PDF를 직접 지원하므로 여러 페이지 가정통신문도 1회 호출로 처리 가능. format 필드를 "jpeg"/"png"/"pdf" 중에서
 * 파일 형식에 맞게 지정하면 된다.
 *
 * <p>ObjectMapper: Spring Boot가 자동으로 빈으로 등록하므로 @RequiredArgsConstructor로 주입받아 사용. IDE에서 빨간 줄이 생길 수
 * 있으나 실제 실행 시에는 정상 주입된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClovaOcrClient {

  private final ClovaOcrProperties ocrProperties;
  private final S3Presigner s3Presigner;

  // Spring Boot AutoConfiguration이 Jackson ObjectMapper를 자동으로 빈 등록함
  // IDE에서 인식 못할 수 있으나 실행 시 정상 주입됨
  private final ObjectMapper objectMapper;

  /**
   * S3에 저장된 가정통신문 파일에 대해 클로바 OCR을 호출. 파일 형식에 따라 클로바 format 값을 자동으로 결정. - PDF: "pdf" → 클로바가 내부적으로 전
   * 페이지 처리 - jpg/jpeg: "jpeg" - png: "png"
   */
  public List<OcrField> callOcr(String bucket, String fileKey) {
    // 1. S3 Presigned URL 생성
    String presignedUrl = generatePresignedUrl(bucket, fileKey);
    log.debug("[ClovaOcr] Presigned URL 생성 완료. fileKey={}", fileKey);

    // 2. 파일 형식 결정
    String format = resolveFormat(fileKey);
    log.debug("[ClovaOcr] 파일 형식 결정. format={}", format);

    // 3. 요청 본문 구성 및 API 호출
    String requestBody = buildRequestBody(presignedUrl, format);
    String responseBody = executeHttpRequest(requestBody);

    // 4. 응답 파싱
    return parseFields(responseBody, fileKey);
  }

  private String generatePresignedUrl(String bucket, String fileKey) {
    GetObjectRequest getObjectRequest =
        GetObjectRequest.builder().bucket(bucket).key(fileKey).build();

    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(ocrProperties.getPresignedUrlMinutes()))
            .getObjectRequest(getObjectRequest)
            .build();

    return s3Presigner.presignGetObject(presignRequest).url().toString();
  }

  /**
   * file_key의 확장자로 클로바 OCR format 값을 결정 클로바 OCR 허용 format 값: "jpeg", "png", "pdf", "tiff" PDF는 클로바가
   * 전 페이지를 처리하므로 별도 변환 불필요.
   */
  private String resolveFormat(String fileKey) {
    String lowerKey = fileKey.toLowerCase();
    if (lowerKey.endsWith(".pdf")) return "pdf";
    if (lowerKey.endsWith(".png")) return "png";
    return "jpeg"; // jpg, jpeg 및 기타
  }

  /** 클로바 OCR API 요청 JSON을 구성. */
  private String buildRequestBody(String presignedUrl, String format) {
    try {
      OcrRequest request =
          new OcrRequest(
              "V2",
              UUID.randomUUID().toString(),
              Instant.now().toEpochMilli(),
              List.of(new OcrImage(format, presignedUrl, "newsletter")));
      return objectMapper.writeValueAsString(request);
    } catch (Exception e) {
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "클로바 OCR 요청 본문 구성 실패: " + e.getMessage());
    }
  }

  private String executeHttpRequest(String requestBody) {
    try {
      HttpClient httpClient =
          HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(ocrProperties.getInvokeUrl()))
              .header("Content-Type", "application/json")
              .header("X-OCR-SECRET", ocrProperties.getSecretKey())
              .POST(HttpRequest.BodyPublishers.ofString(requestBody))
              .timeout(Duration.ofSeconds(60)) // PDF 여러 페이지는 처리 시간이 길 수 있으므로 60초
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.error(
            "[ClovaOcr] API 호출 실패. status={}, body={}", response.statusCode(), response.body());
        throw new ExternalApiException(
            ErrorCode.EXTERNAL_API_ERROR, "클로바 OCR API 오류. status=" + response.statusCode());
      }

      log.debug("[ClovaOcr] API 호출 성공. status={}", response.statusCode());
      return response.body();

    } catch (ExternalApiException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "클로바 OCR API 통신 오류: " + e.getMessage());
    }
  }

  /** 클로바 OCR 응답에서 fields 리스트를 추출. PDF의 경우 여러 페이지가 images 배열에 각각 들어오므로 모든 images의 fields를 합쳐서 반환. */
  private List<OcrField> parseFields(String responseBody, String fileKey) {
    try {
      OcrResponse response = objectMapper.readValue(responseBody, OcrResponse.class);

      if (response.images() == null || response.images().isEmpty()) {
        throw new ExternalApiException(
            ErrorCode.EXTERNAL_API_ERROR, "클로바 OCR 응답에 images 배열이 없습니다.");
      }

      // 모든 페이지(images)의 fields를 하나의 리스트로 합침
      // PDF 여러 페이지인 경우 각 페이지 fields가 순서대로 합쳐짐
      List<OcrField> allFields =
          response.images().stream()
              .peek(
                  image -> {
                    if (!"SUCCESS".equals(image.inferResult())) {
                      log.warn(
                          "[ClovaOcr] 페이지 인식 실패. inferResult={}, fileKey={}",
                          image.inferResult(),
                          fileKey);
                    }
                  })
              .filter(image -> "SUCCESS".equals(image.inferResult()))
              .flatMap(image -> image.fields().stream())
              .toList();

      if (allFields.isEmpty()) {
        throw new ExternalApiException(
            ErrorCode.EXTERNAL_API_ERROR, "클로바 OCR 인식 결과가 없습니다. 모든 페이지가 실패했습니다.");
      }

      log.debug("[ClovaOcr] OCR 완료. totalFieldsCount={}", allFields.size());
      return allFields;

    } catch (ExternalApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "클로바 OCR 응답 파싱 실패: " + e.getMessage());
    }
  }

  /** 클로바 OCR API 요청 본문 */
  record OcrRequest(String version, String requestId, long timestamp, List<OcrImage> images) {}

  /** 요청 내 이미지 정보 */
  record OcrImage(String format, String url, String name) {}

  /** 클로바 OCR API 응답 전체 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record OcrResponse(List<OcrImageResult> images) {}

  /** 응답 내 이미지별 결과 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record OcrImageResult(String inferResult, List<OcrField> fields) {}

  /** OCR 인식 결과 단위 (텍스트 블록). inferText: 인식된 텍스트 boundingPoly: 텍스트 박스의 꼭짓점 좌표 (Y좌표 정렬에 사용) */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Getter
  @NoArgsConstructor
  public static class OcrField {
    private String inferText;
    private BoundingPoly boundingPoly;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BoundingPoly {
      private List<Vertex> vertices;

      @Getter
      @NoArgsConstructor
      @JsonIgnoreProperties(ignoreUnknown = true)
      public static class Vertex {
        private Double x;
        private Double y;
      }
    }
  }
}
