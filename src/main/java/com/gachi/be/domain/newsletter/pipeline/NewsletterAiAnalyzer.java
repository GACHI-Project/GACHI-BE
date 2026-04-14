package com.gachi.be.domain.newsletter.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.checklist.entity.ChecklistItem;
import com.gachi.be.domain.checklist.repository.ChecklistItemRepository;
import com.gachi.be.domain.todo.entity.TodoItem;
import com.gachi.be.domain.todo.repository.TodoItemRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.config.external.OpenAiProperties;
import com.gachi.be.global.exception.ExternalApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenAI를 이용한 가정통신문 AI 분석 컴포넌트.
 * 모든 분석은 OpenAI Chat Completions API를 호출하여 수행.
 * 모델은 application.yml의 app.openai.model 값으로 설정 (기본: gpt-4o-mini). -> 추후 결과 보고 일반 모델로 변경할 수도
 *
 * 프롬프트 설계 원칙:
 * - 시스템 프롬프트: AI의 역할과 출력 형식을 명확히 지정
 * - 사용자 프롬프트: 실제 가정통신문 텍스트
 * - temperature=0.3: 낮은 값으로 설정하여 일관된 결과 보장 (0에 가까울수록 결정적, 1에 가까울수록 창의적)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsletterAiAnalyzer {

    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;
    private final ChecklistItemRepository checklistItemRepository;
    private final TodoItemRepository todoItemRepository;

    /**
     * 가정통신문 전체 AI 분석을 수행하고 결과를 DB에 저장.
     *
     * 분석 기준 텍스트:
     * - 제목/체크리스트/해야할일: originalText (한국어 원문 기준) → 번역 텍스트보다 원문이 날짜, 고유명사 등을 더 정확하게 포함하기 때문
     * - 요약: language=KO면 originalText, 그 외 translatedText 기준 → 사용자 언어로 읽기 편한 요약을 제공하기 위해
     */
    public AiAnalysisResult analyze(
        Long newsletterId,
        String originalText,
        String translatedText,
        String language) {

        log.info("[AiAnalyzer] 분석 시작. newsletterId={}, language={}", newsletterId, language);

        // 요약 기준 텍스트 결정
        String summarySourceText = (translatedText != null && !translatedText.isBlank())
            ? translatedText
            : originalText;

        // 제목 추출
        String title = extractTitle(originalText);
        log.debug("[AiAnalyzer] 제목 추출 완료. title={}", title);

        // AI 요약
        String summary = generateSummary(summarySourceText, language);
        log.debug("[AiAnalyzer] 요약 완료. length={}chars", summary.length());

        // 체크리스트 추출 + DB 저장
        extractAndSaveChecklist(newsletterId, originalText);
        log.debug("[AiAnalyzer] 체크리스트 저장 완료.");

        // 해야 할 일 추출 + DB 저장
        extractAndSaveTodos(newsletterId, originalText);
        log.debug("[AiAnalyzer] 해야 할 일 저장 완료.");

        log.info("[AiAnalyzer] 분석 완료. newsletterId={}", newsletterId);
        return new AiAnalysisResult(title, summary);
    }
    /**
     * 가정통신문 원문에서 제목을 추출.
     *
     * 프롬프트 설명:
     * 시스템: AI에게 "제목만 반환하라"고 명확히 지시.
     *   설명이나 부가 텍스트 없이 제목 텍스트만 반환하도록 함.
     *   30자 제한은 목록 화면에서 잘리지 않기 위한 UI 제약.
     *
     * 사용자: 원문 텍스트를 전달.
     */
    private String extractTitle(String originalText) {
        // 시스템 프롬프트 (AI 역할 + 출력 형식 지정)
        String systemPrompt = """
        당신은 한국 초등학교 가정통신문을 분석하는 전문가입니다.
        가정통신문 텍스트에서 공식 제목을 추출하는 것이 당신의 역할입니다.

        규칙:
        - 가정통신문의 공식 제목만 추출하세요.
        - 30자 이내로 작성하세요.
        - 제목 텍스트만 반환하고, 설명이나 다른 텍스트는 절대 포함하지 마세요.
        - 앞뒤에 마크다운 코드블록(```), 따옴표, 설명을 붙이지 마세요.
        - 제목을 찾을 수 없으면 "가정통신문 안내"를 반환하세요.
        """;
        String response = callOpenAi(systemPrompt, originalText, 100);
        return response.trim();
    }

    /**
     * 가정통신문 핵심 내용을 다문화 학부모를 위해 요약
     * 프롬프트 설명:
     * 시스템: 다문화 학부모 친화적 요약을 위한 지침 제공.
     *   - 언어 코드에 따라 응답 언어 지정
     *   - 날짜, 장소, 준비물, 제출 마감 등 핵심 정보 우선 포함
     *   - 쉬운 표현 사용 (전문 용어 지양)
     *   - 3~5문장 제한으로 핵심만 전달
     * 사용자: KO이면 원문, 그 외이면 번역문을 전달 (사용자 모국어 텍스트 기준으로 요약해야 더 자연스러움)
     */
    private String generateSummary(String sourceText, String language) {
        String responseLanguage = switch (language) {
            case "US" -> "영어(English)";
            case "ZH" -> "중국어 간체(简体中文)";
            case "VI" -> "베트남어(Tiếng Việt)";
            default -> "한국어";
        };

        // 시스템 프롬프트
        String systemPrompt = String.format("""
        당신은 한국 초등학교 가정통신문을 다문화 가정 학부모에게 쉽게 설명해주는 전문가입니다.
        반드시 %s로만 답변하세요.

        요약 규칙:
        - 3~5문장으로 핵심 내용을 요약하세요.
        - 날짜, 장소, 준비물, 제출 마감일을 반드시 포함하세요.
        - 학부모가 바로 이해하고 행동할 수 있도록 명확하게 작성하세요.
        - 어려운 교육 전문 용어는 쉬운 표현으로 바꿔 쓰세요.
        - 요약 텍스트만 반환하고, 제목이나 설명은 포함하지 마세요.
        - 앞뒤에 마크다운 코드블록(```)을 붙이지 마세요.
        """, responseLanguage);

        String response = callOpenAi(systemPrompt, sourceText, 500);
        return response.trim();
    }

    /**
     * 가정통신문에서 체크리스트 항목을 추출하고 DB에 저장.
     * 프롬프트 설명:
     * 시스템: JSON 배열만 반환하도록 엄격히 지정.
     *   - content: 체크리스트 주요 항목 (UI에서 굵게 표시)
     *   - detail: 한 줄 상세 설명 (UI에서 작게 표시)
     *   - 최대 10개로 제한 (너무 많으면 사용자가 압도됨) TODO: 이거 더 얘기해보기
     *   - JSON 외 다른 텍스트 절대 금지 (파싱 실패 방지)
     */
    private void extractAndSaveChecklist(Long newsletterId, String originalText) {
        // 시스템 프롬프트
        String systemPrompt = """
        당신은 한국 초등학교 가정통신문에서 학부모가 해야 할 행동 목록을 추출하는 전문가입니다.

        추출 규칙:
        - 학부모가 직접 해야 할 준비물, 제출물, 확인 사항만 추출하세요.
        - 단순 안내 정보(학교 일정, 공지 등)는 제외하세요.
        - 최대 10개까지만 추출하세요.
        - content: 20자 이내의 핵심 항목명 (예: "현장학습 동의서 제출")
        - detail: 30자 이내의 한 줄 상세 설명 (예: "담임 선생님께 원본 직접 제출")
        - detail이 없으면 문자열 "null"이 아닌 JSON null로 설정하세요.

        출력 규칙:
        - JSON 배열만 반환하세요.
        - 앞뒤에 설명, 제목, 마크다운 코드블록(```json)을 절대 붙이지 마세요.
        - 괄호와 따옴표가 올바르게 닫혔는지 출력 전에 확인하세요.
        형식: [{"content": "항목명", "detail": null}]
        """;

        String response = callOpenAi(systemPrompt, originalText, 800);

        List<ChecklistItemDto> items = parseJsonList(response, new TypeReference<>() {});
        if (items == null || items.isEmpty()) {
            log.warn("[AiAnalyzer] 체크리스트 추출 결과 없음. newsletterId={}", newsletterId);
            return;
        }

        List<ChecklistItem> entities = items.stream()
            .filter(dto -> dto.content() != null && !dto.content().isBlank())
            .map(dto -> ChecklistItem.builder()
                .newsletterId(newsletterId)
                .content(dto.content().trim())
                .detail(dto.detail() != null ? dto.detail().trim() : null)
                .build())
            .toList();

        checklistItemRepository.saveAll(entities);
        log.debug("[AiAnalyzer] 체크리스트 {}개 저장 완료.", entities.size());
    }

    /**
     * 가정통신문에서 날짜 기반 해야 할 일을 추출하고 DB에 저장.
     * 프롬프트 설명:
     * 시스템: 날짜 맥락이 중요한 행동 계획 추출.
     *   - targetDate: 명확한 날짜가 있으면 YYYY-MM-DD, 없으면 null
     *   - targetDateLabel: 사용자에게 보여줄 문구
     *     → 날짜 있으면 "5월 15일", 없으면 "지금 바로" / "행사 전날" 등 맥락에 맞게
     *   - 오늘 날짜를 프롬프트에 주입하여 "내일", "이번 주" 등 상대적 표현을 절대 날짜로 변환
     *   - 최대 5개로 제한 (너무 많으면 핵심이 희석됨) TODO: 이거 더 얘기해보기
     */
    private void extractAndSaveTodos(Long newsletterId, String originalText) {
        // 오늘 날짜를 프롬프트에 주입 (상대적 날짜 표현 변환을 위해)
        String today = LocalDate.now().toString(); // 예: "2026-04-13"

        // 시스템 프롬프트
        String systemPrompt = String.format("""
        당신은 한국 초등학교 가정통신문에서 학부모가 해야 할 일을 날짜 기준으로 추출하는 전문가입니다.
        오늘 날짜는 %s입니다. 이 날짜를 기준으로 "내일", "이번 주" 같은 상대적 표현을 실제 날짜로 변환하세요.

        추출 규칙:
        - 날짜나 기한이 있는 행동만 추출하세요.
        - 최대 5개까지만 추출하세요. 가장 중요하고 긴급한 것 우선으로 선택하세요.
        - content: 40자 이내의 구체적인 행동 (예: "담임 선생님께 동의서 직접 제출")
        - targetDate: 명확한 날짜가 있으면 YYYY-MM-DD 형식, 없으면 null
        - targetDateLabel: 사용자에게 보여줄 날짜 문구
          → 오늘이면 "오늘", 날짜 있으면 "N월 N일", 즉시 해야 하면 "지금 바로", 없으면 "행사 전날" 등

        출력 규칙:
        - JSON 배열만 반환하세요.
        - 앞뒤에 설명, 제목, 마크다운 코드블록(```json)을 절대 붙이지 마세요.
        - 괄호와 따옴표가 올바르게 닫혔는지 출력 전에 확인하세요.
        형식: [{"content": "할 일", "targetDate": "2026-05-15 또는 null", "targetDateLabel": "표시 문구"}]
        """, today);

        String response = callOpenAi(systemPrompt, originalText, 800);

        List<TodoItemDto> items = parseJsonList(response, new TypeReference<>() {});
        if (items == null || items.isEmpty()) {
            log.warn("[AiAnalyzer] 해야 할 일 추출 결과 없음. newsletterId={}", newsletterId);
            return;
        }

        List<TodoItem> entities = items.stream()
            .filter(dto -> dto.content() != null && !dto.content().isBlank())
            .map(dto -> {
                LocalDate targetDate = null;
                if (dto.targetDate() != null && !dto.targetDate().isBlank()
                    && !"null".equals(dto.targetDate())) {
                    try {
                        targetDate = LocalDate.parse(dto.targetDate());
                    } catch (Exception e) {
                        log.warn("[AiAnalyzer] targetDate 파싱 실패. value={}", dto.targetDate());
                    }
                }
                return TodoItem.builder()
                    .newsletterId(newsletterId)
                    .content(dto.content().trim())
                    .targetDate(targetDate)
                    .targetDateLabel(dto.targetDateLabel())
                    .build();
            })
            .toList();

        todoItemRepository.saveAll(entities);
        log.debug("[AiAnalyzer] 해야 할 일 {}개 저장 완료.", entities.size());
    }
    /**
     * OpenAI Chat Completions API 호출.
     * @param systemPrompt AI 역할과 출력 형식을 지정하는 시스템 메시지
     * @param userContent  분석할 가정통신문 텍스트 (사용자 메시지)
     * @param maxTokens    응답 최대 토큰 수 (작업마다 다르게 설정)
     * @return AI 응답 텍스트
     */
    private String callOpenAi(String systemPrompt, String userContent, int maxTokens) {
        try {
            // 요청 본문 구성
            String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", openAiProperties.getModel(),
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContent)
                ),
                "temperature", 0.3,       // 낮은 값 = 일관된 결과 (0: 결정적, 1: 창의적)
                "max_tokens", maxTokens
            ));

            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openAiProperties.getApiUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60)) // GPT 응답 시간 고려하여 60초
                .build();

            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("[AiAnalyzer] OpenAI API 호출 실패. status={}, body={}",
                    response.statusCode(), response.body());
                throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR,
                    "OpenAI API 오류. status=" + response.statusCode());
            }

            return parseOpenAiResponse(response.body());

        } catch (ExternalApiException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR,
                "OpenAI API 통신 오류: " + e.getMessage());
        }
    }

    /**
     * OpenAI 응답에서 텍스트 내용을 추출.
     */
    private String parseOpenAiResponse(String responseBody) {
        try {
            OpenAiResponse response = objectMapper.readValue(responseBody, OpenAiResponse.class);

            if (response.choices() == null || response.choices().isEmpty()) {
                throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR,
                    "OpenAI 응답에 choices가 없습니다.");
            }

            String content = response.choices().get(0).message().content();
            if (content == null || content.isBlank()) {
                throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR,
                    "OpenAI 응답 content가 비어있습니다.");
            }

            return content.trim();

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR,
                "OpenAI 응답 파싱 실패: " + e.getMessage());
        }
    }

    /**
     * OpenAI가 반환한 JSON 문자열을 List로 파싱
     * GPT가 간혹 JSON 앞뒤에 ```json ... ``` 마크다운을 붙이는 경우가 있어서 파싱 전에 제거하는 전처리를 수행
     */
    private <T> List<T> parseJsonList(String jsonText, TypeReference<List<T>> typeRef) {
        try {
            // 마크다운 코드블록 제거
            String cleaned = jsonText
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

            return objectMapper.readValue(cleaned, typeRef);
        } catch (Exception e) {
            log.error("[AiAnalyzer] JSON 파싱 실패. text={}, error={}", jsonText, e.getMessage());
            return List.of();
        }
    }

    /** AI 분석 결과 (제목 + 요약). 체크리스트/해야할일은 DB에 직접 저장됨. */
    public record AiAnalysisResult(String title, String summary) {}

    /** 체크리스트 OpenAI 응답 DTO */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChecklistItemDto(String content, String detail) {}

    /** 해야 할 일 OpenAI 응답 DTO */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TodoItemDto(String content, String targetDate, String targetDateLabel) {}

    /** OpenAI Chat Completions API 응답 구조 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAiResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {}
}
