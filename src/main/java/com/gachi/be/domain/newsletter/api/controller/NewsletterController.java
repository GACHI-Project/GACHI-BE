package com.gachi.be.domain.newsletter.api.controller;

import com.gachi.be.domain.newsletter.dto.response.NewsletterStatusResponse;
import com.gachi.be.domain.newsletter.dto.response.NewsletterUploadResponse;
import com.gachi.be.domain.newsletter.service.NewsletterService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * TODO: JWT 필터 연결 후 userId를 SecurityContext에서 추출하도록 변경. Long userId = ((UserPrincipal)
 * SecurityContextHolder .getContext().getAuthentication().getPrincipal()).getId();
 */
@Tag(name = "Newsletter", description = "가정통신문 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/newsletters")
public class NewsletterController {

  private final NewsletterService newsletterService;

  /**
   * 가정통신문 업로드 API.
   *
   * <p>요청 형식: multipart/form-data Swagger에서 "file" 파라미터를 통해 직접 파일을 선택해서 테스트 가능. 업로드 성공 시
   * newsletterId를 받고, 이 ID로 /status API를 폴링 O.
   */
  @Operation(
      summary = "가정통신문 업로드",
      description =
          """
      가정통신문 이미지(jpg/png) 또는 PDF를 S3에 업로드하고 AI 분석을 시작합니다.
      응답으로 받은 newsletterId로 /status API를 폴링하여 진행률을 확인하세요.
      """)
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<NewsletterUploadResponse> upload(
      @Parameter(
              description = "가정통신문 파일 (jpg/png/pdf, 최대 10MB)",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                      schema = @Schema(type = "string", format = "binary")))
          @RequestPart("file")
          MultipartFile file,
      @Parameter(description = "연결할 자녀 ID. 미선택 시 생략") @RequestParam(required = false) Long childId,
      @Parameter(description = "언어 코드 (KO/US/ZH/VI). 기본값 KO") @RequestParam(defaultValue = "KO")
          String language) {

    // TODO: JWT 연결 후 SecurityContext에서 추출
    Long userId = 1L; // 임시 하드코딩

    NewsletterUploadResponse response = newsletterService.upload(userId, file, childId, language);
    return ApiResponse.success(SuccessCode.NEWSLETTER_UPLOAD_SUCCESS, response);
  }

  /**
   * 가정통신문 분석 상태 조회 (폴링) API.
   *
   * <p>스캔 중 화면에서 2초마다 호출하여 진행률을 표시한다. PENDING(0%) → 업로드 직후 대기 상태 PROCESSING(60%) → OCR/번역/요약 진행 중
   * COMPLETED(100%) → 분석 완료, 결과 화면으로 이동 FAILED(0%) → 실패, errorMessage 확인
   */
  @Operation(
      summary = "분석 상태 조회 (폴링)",
      description =
          """
      업로드 후 AI 분석 진행률을 확인합니다. 2초 간격으로 폴링하세요.
      status가 COMPLETED이면 결과 조회 API를 호출하면 됩니다.
      """)
  @GetMapping("/{newsletterId}/status")
  public ApiResponse<NewsletterStatusResponse> getStatus(
      @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId) {

    // TODO: JWT 연결 후 SecurityContext에서 추출
    Long userId = 1L; // 임시 하드코딩

    NewsletterStatusResponse response = newsletterService.getStatus(userId, newsletterId);
    return ApiResponse.success(SuccessCode.NEWSLETTER_STATUS_SUCCESS, response);
  }
}
