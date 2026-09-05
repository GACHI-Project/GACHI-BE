package com.gachi.be.domain.newsletter.api.controller;

import com.gachi.be.domain.calendar.dto.request.CalendarDateUpdateRequest;
import com.gachi.be.domain.calendar.dto.request.CalendarPreviewMockRequest;
import com.gachi.be.domain.calendar.dto.request.CalendarRegisterRequest;
import com.gachi.be.domain.calendar.dto.response.CalendarPreviewResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarRegisterResponse;
import com.gachi.be.domain.calendar.service.CalendarPreviewMockService;
import com.gachi.be.domain.calendar.service.CalendarRegisterService;
import com.gachi.be.domain.newsletter.dto.response.*;
import com.gachi.be.domain.newsletter.service.NewsletterService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Newsletter", description = "가정통신문 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/newsletters")
public class NewsletterController {

  private final NewsletterService newsletterService;
  private final CalendarRegisterService calendarRegisterService;
  private final CalendarPreviewMockService calendarPreviewMockService;

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
      이미지는 한 번에 최대 10장까지 보낼 수 있으며, files 배열 순서가 그대로 문서 페이지 순서가 됩니다.
      PDF는 그 자체가 여러 페이지를 담는 형식이므로 1개만 허용하며 이미지와 함께 보낼 수 없습니다.
      크기 제한: 장당 최대 10MB, 전체 합계 최대 50MB.
      1장만 올릴 때도 반드시 files로 전송해야 합니다.
      응답으로 받은 newsletterId로 /status API를 폴링하여 진행률을 확인합니다.
      """)
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<NewsletterUploadResponse> upload(
      @AuthenticationPrincipal Long userId,
      @Parameter(
              description = "가정통신문 파일 목록 (jpg/png 최대 10장 또는 pdf 1개, 장당 최대 10MB)",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                      array = @ArraySchema(schema = @Schema(type = "string", format = "binary"))))
          @RequestPart("files")
          List<MultipartFile> files,
      @Parameter(description = "연결할 자녀 ID. 미선택 시 생략") @RequestParam(required = false)
          Long childId) {

    NewsletterUploadResponse response = newsletterService.upload(userId, files, childId);
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

  /** 실패한 가정통신문 분석 재시도 API */
  @Operation(
      summary = "가정통신문 분석 재시도",
      description =
          """
      FAILED 상태의 가정통신문 분석을 다시 시작합니다.
      AI 서버 장애로 실패한 경우 기존 OCR/번역 결과는 보존되어 있고, 재시도 시 파이프라인이 다시 실행됩니다.
      """)
  @PostMapping("/{newsletterId}/analysis/retry")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiResponse<NewsletterUploadResponse> retryAnalysis(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId) {

    NewsletterUploadResponse response = newsletterService.retryAnalysis(userId, newsletterId);
    return ApiResponse.success(SuccessCode.NEWSLETTER_RETRY_ACCEPTED, response);
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

  /** 요약 결과 조회 API. */
  @Operation(
      summary = "요약 결과 조회",
      description =
          """
       스캔 결과 [AI요약] 탭 상단의 AI 생성 요약문을 반환합니다.
       스캔 직후와 문서 상세보기 모두에서 사용됩니다.
       탭 노출 여부는 프론트엔드가 GET /newsletters/{id}의 isCalendarRegistered 값으로 제어합니다.
       """)
  @GetMapping("/{newsletterId}/summary")
  public ApiResponse<NewsletterSummaryResponse> getSummary(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId) {

    NewsletterSummaryResponse response = newsletterService.getSummary(userId, newsletterId);
    return ApiResponse.success(SuccessCode.NEWSLETTER_SUMMARY_SUCCESS, response);
  }

  /** 체크리스트 탭 조회 */
  @Operation(
      summary = "체크리스트/해야할일 조회",
      description =
          """
      type 파라미터로 반환 항목을 필터링합니다.
      CHECKLIST: 체크리스트 탭 항목 (dueDate 포함)
      TODO: AI요약 탭 해야할 일 (targetDate, targetDateLabel 포함)
      미전송: 전체 반환
      """)
  @GetMapping("/{newsletterId}/checklist")
  public ApiResponse<NewsletterChecklistResponse> getChecklist(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId,
      @Pattern(regexp = "CHECKLIST|TODO", message = "type은 CHECKLIST 또는 TODO 여야 합니다.")
          @RequestParam(required = false)
          String type) {

    // type을 서비스로 그대로 넘김. 유효성 검사는 서비스에서 처리.
    NewsletterChecklistResponse response =
        newsletterService.getChecklist(userId, newsletterId, type);
    return ApiResponse.success(SuccessCode.NEWSLETTER_CHECKLIST_SUCCESS, response);
  }

  /** 가정통신문 상세 조회 */
  @Operation(
      summary = "가정통신문 상세 조회",
      description =
          """
        문서 목록에서 특정 가정통신문 클릭 시 호출됩니다.
        isCalendarRegistered=true이면 전체문서+체크리스트+AI요약 탭 모두 표시.
        isCalendarRegistered=false이면 전체문서 탭만 표시.
        """)
  @GetMapping("/{newsletterId}")
  public ApiResponse<NewsletterDetailResponse> getDetail(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId) {

    NewsletterDetailResponse response = newsletterService.getDetail(userId, newsletterId);
    return ApiResponse.success(SuccessCode.NEWSLETTER_DETAIL_SUCCESS, response);
  }

  /** 가정통신문 목록 조회 */
  @Operation(
      summary = "가정통신문 목록 조회",
      description =
          """
        문서 목록 화면. 자녀 필터, 제목 검색, 페이지네이션을 지원합니다.
        각 항목의 isCalendarRegistered로 상세보기 탭 구성을 결정합니다.
        """)
  @GetMapping
  public ApiResponse<NewsletterListResponse> getList(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "자녀 이름 필터 (미전송 시 전체)") @RequestParam(required = false)
          String childName,
      @Parameter(description = "제목 검색 키워드 (미전송 시 전체)") @RequestParam(required = false)
          String search,
      @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "정렬: recent(최신순, 기본값) / oldest(오래된순)")
          @RequestParam(defaultValue = "recent")
          String sort) {

    NewsletterListResponse response =
        newsletterService.getList(userId, childName, search, page, sort);
    return ApiResponse.success(SuccessCode.NEWSLETTER_LIST_SUCCESS, response);
  }

  /** 홈화면 가정통신문 조회 */
  @Operation(
      summary = "홈화면 최근 7일 가정통신문 조회",
      description =
          """
        오늘 KST 기준 최근 7일 동안 스캔된 가정통신문을 날짜별로 그룹핑하여 반환합니다.
        해당 날짜에 스캔된 가정통신문이 없으면 그 날짜는 결과에 포함되지 않습니다.
        """)
  @GetMapping("/recent")
  public ApiResponse<NewsletterRecentResponse> getRecent(@AuthenticationPrincipal Long userId) {

    NewsletterRecentResponse response = newsletterService.getRecent(userId);
    return ApiResponse.success(SuccessCode.NEWSLETTER_RECENT_SUCCESS, response);
  }

  /** 캘린더 preview 더미 데이터 Redis 주입 API TODO: 임시 API 임 */
  @Operation(
      summary = "[임시] 캘린더 preview 더미 데이터 주입",
      description =
          """
          AI 파이프라인 연결 전 테스트용 임시 API입니다.
          호출하면 Redis에 preview 데이터가 저장되어 GET /preview가 정상 동작합니다.
          checklistIds 생략 시 해당 newsletter의 CHECKLIST 항목을 자동 균등 배분합니다.
          AI 파이프라인 완성 후 제거 예정입니다.
          """)
  @PostMapping("/{newsletterId}/calendar/preview/mock")
  public ApiResponse<Void> injectMockPreview(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId,
      @Valid @RequestBody CalendarPreviewMockRequest request) {

    calendarPreviewMockService.injectMockPreview(userId, newsletterId, request);
    return ApiResponse.success(SuccessCode.CALENDAR_PREVIEW_MOCK_SUCCESS, null);
  }

  /** 캘린더 일정 미리보기 조회. */
  @Operation(
      summary = "캘린더 일정 미리보기 조회",
      description =
          """
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

  /** 캘린더 일정 날짜 수정. */
  @Operation(
      summary = "캘린더 일정 날짜 수정",
      description =
          """
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

  /** 캘린더 일정 등록 */
  @Operation(
      summary = "캘린더 일정 등록 (저장하기)",
      description =
          """
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

    CalendarRegisterResponse response =
        calendarRegisterService.register(userId, newsletterId, request);
    return ApiResponse.success(SuccessCode.CALENDAR_REGISTER_SUCCESS, response);
  }

  /** 대화 주제 조회 추천 */
  @Operation(
      summary = "대화 주제 조회",
      description =
          """
      AI가 가정통신문에서 추출한 자녀와의 대화 주제 목록을 반환합니다. (최대 3개)
      자녀와 직접 연관된 내용만 추출되며, 대화 주제가 없으면 빈 배열을 반환합니다.
      """)
  @GetMapping("/{newsletterId}/conversation-topics")
  public ApiResponse<ConversationTopicResponse> getConversationTopics(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId) {

    ConversationTopicResponse response =
        newsletterService.getConversationTopics(userId, newsletterId);
    return ApiResponse.success(SuccessCode.NEWSLETTER_CONVERSATION_TOPICS_SUCCESS, response);
  }

  /** 문화 맥락 안내 조회 */
  @Operation(
      summary = "문화 맥락 안내 조회",
      description =
          """
       AI가 가정통신문과 관련 있다고 판단한 학교 생활 가이드(FAQ)를 Q/A 형태로 반환합니다. (최대 2개)
       질문과 답변은 school_guide DB에 저장된 검수된 원문을 사용자 언어로 반환합니다.
       관련 FAQ가 없으면 빈 배열([])을 반환합니다.
       스캔 직후와 문서 상세보기 모두에서 사용됩니다.
       탭 노출 여부는 프론트엔드가 GET /newsletters/{id}의 isCalendarRegistered 값으로 제어합니다.
       """)
  @GetMapping("/{newsletterId}/cultural-guides")
  public ApiResponse<NewsletterCulturalGuideResponse> getCulturalGuides(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "가정통신문 ID", required = true) @PathVariable Long newsletterId) {

    NewsletterCulturalGuideResponse response =
        newsletterService.getCulturalGuides(userId, newsletterId);
    return ApiResponse.success(SuccessCode.NEWSLETTER_CULTURAL_GUIDES_SUCCESS, response);
  }
}
