package com.gachi.be.domain.newsletter.api.controller;

import com.gachi.be.domain.calendar.dto.request.CalendarDateUpdateRequest;
import com.gachi.be.domain.calendar.dto.request.CalendarRegisterRequest;
import com.gachi.be.domain.calendar.dto.response.CalendarPreviewResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarRegisterResponse;
import com.gachi.be.domain.calendar.service.CalendarRegisterService;
import com.gachi.be.domain.newsletter.dto.response.NewsletterChecklistResponse;
import com.gachi.be.domain.newsletter.dto.response.NewsletterStatusResponse;
import com.gachi.be.domain.newsletter.dto.response.NewsletterTranslationResponse;
import com.gachi.be.domain.newsletter.dto.response.NewsletterUploadResponse;
import com.gachi.be.domain.newsletter.service.NewsletterService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Newsletter", description = "가정통신문 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/newsletters")
public class NewsletterController {

  private final NewsletterService newsletterService;
    private final CalendarRegisterService calendarRegisterService;

    /**
   * 가정통신문 업로드 API.
   *
   * 요청 형식: multipart/form-data Swagger에서 "file" 파라미터를 통해 직접 파일을 선택해서 테스트 가능. 업로드 성공 시
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
      @AuthenticationPrincipal Long userId,
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
      @Parameter(description = "언어 코드 (KO/US/ZH/VI). 기본값 KO")
          @RequestParam(defaultValue = "KO")
          @Pattern(regexp = "KO|US|ZH|VI", message = "language는 KO/US/ZH/VI 중 하나여야 합니다.")
          String language) {

    NewsletterUploadResponse response = newsletterService.upload(userId, file, childId, language);
    return ApiResponse.success(SuccessCode.NEWSLETTER_UPLOAD_SUCCESS, response);
  }

  /** 가정통신문 분석 상태 조회 (폴링) API */
  @Operation(
      summary = "분석 상태 조회 (폴링)",
      description =
          """
      업로드 후 AI 분석 진행률을 확인합니다. 2초 간격으로 폴링하세요.
      status가 COMPLETED이면 결과 조회 API를 호출하면 됩니다.
      """)
  @GetMapping("/{newsletterId}/status")
  public ApiResponse<NewsletterStatusResponse> getStatus(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId) {

    NewsletterStatusResponse response = newsletterService.getStatus(userId, newsletterId);
    return ApiResponse.success(SuccessCode.NEWSLETTER_STATUS_SUCCESS, response);
  }

  /** 번역 결과 조회 API. */
  @Operation(
      summary = "번역 결과 조회",
      description =
          """
        분석이 완료된(COMPLETED) 가정통신문만 조회 가능합니다.
        언어가 KO인 경우 translatedText 필드는 응답에 포함되지 않습니다.
        """)
  @GetMapping("/{newsletterId}/translation")
  public ApiResponse<NewsletterTranslationResponse> getTranslation(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId) {

    NewsletterTranslationResponse response = newsletterService.getTranslation(userId, newsletterId);
    return ApiResponse.success(SuccessCode.NEWSLETTER_TRANSLATION_SUCCESS, response);
  }

  /** 체크리스트 탭 조회 */
    @Operation(summary = "체크리스트 조회", description = """
      스캔 결과 체크리스트 탭에 표시할 항목을 반환합니다.
      CHECKLIST 타입만 반환됩니다 (TODO는 AI요약 탭 전용).
      dueDate는 연결된 캘린더 일정의 날짜(YYYY-MM-DD)입니다.
      캘린더 등록 전이면 dueDate=null입니다.
      """)
    @GetMapping("/{newsletterId}/checklist")
    public ApiResponse<NewsletterChecklistResponse> getChecklist(
        @AuthenticationPrincipal Long userId,
        @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId) {

        NewsletterChecklistResponse response = newsletterService.getChecklist(userId, newsletterId);
        return ApiResponse.success(SuccessCode.NEWSLETTER_CHECKLIST_SUCCESS, response);
    }

    /** 캘린더 일정 미리보기 조회. */
    @Operation(summary = "캘린더 일정 미리보기 조회", description = """
      저장하기 버튼 클릭 시 팝업에 표시할 AI 추출 일정 목록을 반환합니다.
      Redis에 임시 저장된 데이터를 읽어 반환합니다.
      데이터가 없거나 만료된 경우(1시간 TTL) 404를 반환합니다.
      """)
    @GetMapping("/{newsletterId}/calendar/preview")
    public ApiResponse<CalendarPreviewResponse> getCalendarPreview(
        @AuthenticationPrincipal Long userId,
        @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId) {

        CalendarPreviewResponse response = calendarRegisterService.getPreview(userId, newsletterId);
        return ApiResponse.success(SuccessCode.CALENDAR_PREVIEW_SUCCESS, response);
    }

    /**캘린더 일정 날짜 수정.*/
    @Operation(summary = "캘린더 일정 날짜 수정", description = """
      팝업에서 수정 버튼 클릭 시 잘못 추출된 날짜를 수정합니다.
      tempEventId로 어떤 일정을 수정할지 식별합니다.
      수정 결과는 Redis에 저장되며, POST /calendar 등록 시 반영됩니다.
      """)
    @PatchMapping("/{newsletterId}/calendar/dates")
    public ApiResponse<Void> updateCalendarDates(
        @AuthenticationPrincipal Long userId,
        @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId,
        @Valid @RequestBody CalendarDateUpdateRequest request) {

        calendarRegisterService.updateDates(userId, newsletterId, request);
        return ApiResponse.success(SuccessCode.CALENDAR_DATES_UPDATED, null);
    }

    /** 캘린더 일정 등록*/
    @Operation(summary = "캘린더 일정 등록 (저장하기)", description = """
      팝업에서 "네, 등록할게요" 선택 시 calendar_events에 일정을 등록합니다.
      등록 후 체크리스트·AI요약이 문서 목록에 노출됩니다.
      external_key로 중복 등록이 방지됩니다.
      """)
    @PostMapping("/{newsletterId}/calendar")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CalendarRegisterResponse> registerCalendar(
        @AuthenticationPrincipal Long userId,
        @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId,
        @Valid @RequestBody CalendarRegisterRequest request) {

        CalendarRegisterResponse response = calendarRegisterService.register(userId, newsletterId, request);
        return ApiResponse.success(SuccessCode.CALENDAR_REGISTER_SUCCESS, response);
    }
}
