package com.gachi.be.domain.newsletter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.NewsletterPage;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPageStatus;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPausedReason;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPausedStage;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrField;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrPageResult;
import com.gachi.be.domain.newsletter.pipeline.NewsletterAiAnalyzer.AiAnalysisResult;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.file.config.S3Properties;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.ExternalApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

// 페이지 단위 파이프라인의 멈춤(PAUSED) / 완료 흐름 테스트
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsletterPipelineServiceTest {

    private static final Long NEWSLETTER_ID = 1L;
    private static final Long PAGE_ID = 100L;

    @Mock private NewsletterRepository newsletterRepository;
    @Mock private S3Client s3Client;
    @Mock private S3Properties s3Properties;
    @Mock private ImagePreprocessor imagePreprocessor;
    @Mock private ClovaOcrClient clovaOcrClient;
    @Mock private PapagoTranslateClient papagoTranslateClient;
    @Mock private NewsletterAiAnalyzer newsletterAiAnalyzer;
    @Mock private NewsletterDateCandidateService newsletterDateCandidateService;
    @Mock private NewsletterPipelineStatusService newsletterPipelineStatusService;
    @Mock private NewsletterContentHasher newsletterContentHasher;
    @Mock private NewsletterCulturalGuideService newsletterCulturalGuideService;
    @Mock private NewsletterPageService newsletterPageService;

    private NewsletterPipelineService pipelineService;

    @BeforeEach
    void setUp() {
        pipelineService =
            new NewsletterPipelineService(
                newsletterRepository,
                s3Client,
                s3Properties,
                imagePreprocessor,
                clovaOcrClient,
                new OcrTextRefiner(),
                papagoTranslateClient,
                newsletterAiAnalyzer,
                newsletterDateCandidateService,
                newsletterPipelineStatusService,
                newsletterContentHasher,
                newsletterCulturalGuideService,
                newsletterPageService,
                new OcrBlockGrouper(),
                Clock.fixed(Instant.parse("2026-10-03T01:00:00Z"), ZoneId.of("Asia/Seoul")));
        when(s3Properties.getBucket()).thenReturn("bucket");
        when(newsletterContentHasher.hash(anyString())).thenReturn(Optional.of("content-hash"));
    }

    @Test
    void imageOcrCommunicationFailurePausesAfterOneAutoRetry() {
        givenNewsletter("newsletters/page1.jpg", "VI");
        NewsletterPage page = imagePage(NewsletterPageStatus.PENDING);
        when(newsletterPageService.preparePages(NEWSLETTER_ID)).thenReturn(List.of(page));
        when(clovaOcrClient.callOcrPages(anyString(), anyString()))
            .thenThrow(new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "timeout"));

        pipelineService.runPipeline(NEWSLETTER_ID);

        // 최초 1회 + 자동 재시도 1회
        verify(clovaOcrClient, times(2)).callOcrPages("bucket", "newsletters/page1.jpg");
        verify(newsletterPageService).markOcrFailed(PAGE_ID);
        verify(newsletterPipelineStatusService)
            .markPaused(
                eq(NEWSLETTER_ID),
                eq(1),
                eq(NewsletterPausedStage.OCR),
                eq(NewsletterPausedReason.OCR_FAILED),
                any(),
                isNull(),
                isNull());
        verify(newsletterAiAnalyzer, never()).analyze(anyLong(), any(), any(), any(), anyList());
    }

    @Test
    void imageWithoutRecognizedTextPausesAsUnreadableWithoutAutoRetry() {
        givenNewsletter("newsletters/page1.jpg", "VI");
        NewsletterPage page = imagePage(NewsletterPageStatus.PENDING);
        when(newsletterPageService.preparePages(NEWSLETTER_ID)).thenReturn(List.of(page));
        when(clovaOcrClient.callOcrPages(anyString(), anyString()))
            .thenReturn(List.of(new OcrPageResult(0, false, List.of())));

        pipelineService.runPipeline(NEWSLETTER_ID);

        verify(clovaOcrClient, times(1)).callOcrPages(anyString(), anyString());
        verify(newsletterPageService).markUnreadable(PAGE_ID);
        verify(newsletterPipelineStatusService)
            .markPaused(
                eq(NEWSLETTER_ID),
                eq(1),
                eq(NewsletterPausedStage.OCR),
                eq(NewsletterPausedReason.UNREADABLE),
                any(),
                isNull(),
                isNull());
    }

    @Test
    void pdfTranslationFailurePausesWithOriginalTextSnapshot() {
        givenNewsletter("newsletters/doc.pdf", "VI");
        NewsletterPage page = pdfPage(NewsletterPageStatus.OCR_DONE);
        // PDF 페이지가 이미 있으면(이어서 진행) 클로바를 다시 부르지 않는다.
        when(newsletterPageService.preparePages(NEWSLETTER_ID)).thenReturn(List.of(page));
        when(papagoTranslateClient.translate(anyString(), eq("VI")))
            .thenThrow(new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "papago down"));

        pipelineService.runPipeline(NEWSLETTER_ID);

        verify(clovaOcrClient, never()).callOcrPages(anyString(), anyString());
        verify(papagoTranslateClient, times(2)).translate("가정통신문 원문", "VI");
        verify(newsletterPageService).markTranslationFailed(PAGE_ID);
        verify(newsletterPipelineStatusService)
            .markPaused(
                eq(NEWSLETTER_ID),
                eq(1),
                eq(NewsletterPausedStage.TRANSLATION),
                eq(NewsletterPausedReason.TRANSLATION_FAILED),
                any(),
                eq("가정통신문 원문"),
                eq("가정통신문 원문"));
        verify(newsletterAiAnalyzer, never()).analyze(anyLong(), any(), any(), any(), anyList());
    }

    @Test
    void imagePageSuccessRunsAiAnalysisOnceWithDisplayImage() throws Exception {
        givenNewsletter("newsletters/page1.jpg", "KO");
        NewsletterPage pending = imagePage(NewsletterPageStatus.PENDING);
        NewsletterPage ocrDone = imagePage(NewsletterPageStatus.PENDING);
        ocrDone.completeOcr("가정통신문", "가정통신문", null);
        NewsletterPage success = imagePage(NewsletterPageStatus.PENDING);
        success.completeOcr("가정통신문", "가정통신문", null);
        success.completeTranslation(null, null);

        when(newsletterPageService.preparePages(NEWSLETTER_ID)).thenReturn(List.of(pending));
        when(newsletterPageService.findPages(NEWSLETTER_ID))
            .thenReturn(List.of(ocrDone))
            .thenReturn(List.of(success));
        when(clovaOcrClient.callOcrPages(anyString(), anyString()))
            .thenReturn(List.of(new OcrPageResult(0, true, List.of(field("가정통신문")))));
        when(newsletterAiAnalyzer.analyze(anyLong(), anyString(), any(), anyString(), anyList()))
            .thenReturn(new AiAnalysisResult("제목", Map.of(), "요약"));

        pipelineService.runPipeline(NEWSLETTER_ID);

        verify(newsletterPageService).completeOcr(eq(PAGE_ID), eq("가정통신문"), eq("가정통신문"), anyList());
        // 한국어 사용자는 번역 없이 페이지 완료
        verify(newsletterPageService).completeTranslation(PAGE_ID, null, null);
        verify(papagoTranslateClient, never()).translate(anyString(), anyString());
        verify(newsletterAiAnalyzer, times(1))
            .analyze(eq(NEWSLETTER_ID), eq("가정통신문"), isNull(), eq("KO"), anyList());
        verify(newsletterPipelineStatusService)
            .markCompleted(
                eq(NEWSLETTER_ID), eq("가정통신문"), eq("가정통신문"), isNull(), eq("제목"), any(), eq("요약"));
        verify(newsletterPipelineStatusService, never())
            .markPaused(anyLong(), anyInt(), any(), any(), any(), any(), any());
    }

    private void givenNewsletter(String fileKey, String language) {
        Newsletter newsletter =
            Newsletter.builder()
                .userId(10L)
                .fileKey(fileKey)
                .fileKeys(List.of(fileKey))
                .fileHash("hash")
                .status(NewsletterStatus.PENDING)
                .language(language)
                .build();
        ReflectionTestUtils.setField(newsletter, "id", NEWSLETTER_ID);
        when(newsletterRepository.findById(NEWSLETTER_ID)).thenReturn(Optional.of(newsletter));
    }

    /** 표시용 이미지가 이미 준비된 이미지 페이지 (S3 다운로드/전처리 생략) */
    private NewsletterPage imagePage(NewsletterPageStatus status) {
        NewsletterPage page =
            NewsletterPage.builder()
                .newsletterId(NEWSLETTER_ID)
                .pageNo(1)
                .fileKey("newsletters/page1.jpg")
                .status(status)
                .build();
        page.updateDisplayImage("newsletters/page1.jpg", 1000, 1000);
        ReflectionTestUtils.setField(page, "id", PAGE_ID);
        return page;
    }

    private NewsletterPage pdfPage(NewsletterPageStatus status) {
        NewsletterPage page =
            NewsletterPage.builder()
                .newsletterId(NEWSLETTER_ID)
                .pageNo(1)
                .fileKey("newsletters/doc.pdf")
                .status(NewsletterPageStatus.PENDING)
                .build();
        if (status == NewsletterPageStatus.OCR_DONE) {
            page.completeOcr("가정통신문 원문", "가정통신문 원문", null);
        }
        ReflectionTestUtils.setField(page, "id", PAGE_ID);
        return page;
    }

    private OcrField field(String text) throws Exception {
        String json =
            """
            {"inferText":"%s","boundingPoly":{"vertices":[
              {"x":100,"y":100},{"x":300,"y":100},{"x":300,"y":140},{"x":100,"y":140}]}}
            """
                .formatted(text);
        return new ObjectMapper().readValue(json, OcrField.class);
    }

    @Test
    void pausedReturnValueIsOnlyLogged() {
        // markPaused가 false(이미 FAILED 등)를 반환해도 파이프라인은 예외 없이 끝난다.
        givenNewsletter("newsletters/page1.jpg", "VI");
        NewsletterPage page = imagePage(NewsletterPageStatus.PENDING);
        when(newsletterPageService.preparePages(NEWSLETTER_ID)).thenReturn(List.of(page));
        when(clovaOcrClient.callOcrPages(anyString(), anyString()))
            .thenReturn(List.of(new OcrPageResult(0, false, List.of())));
        when(newsletterPipelineStatusService.markPaused(
            anyLong(), anyInt(), any(), any(), any(), any(), any()))
            .thenReturn(false);

        pipelineService.runPipeline(NEWSLETTER_ID);

        verify(newsletterPipelineStatusService, never())
            .markFailedWithSnapshot(anyLong(), any(), any(), any(), anyString(), anyString());
        assertThat(page.getPageNo()).isEqualTo(1);
    }
}
