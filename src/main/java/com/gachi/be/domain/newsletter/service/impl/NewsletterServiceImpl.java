package com.gachi.be.domain.newsletter.service.impl;

import com.gachi.be.domain.calendar.entity.CalendarEvent;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.child.entity.Child;
import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.newsletter.dto.response.*;
import com.gachi.be.domain.newsletter.dto.response.ConversationTopicResponse;
import com.gachi.be.domain.newsletter.dto.response.NewsletterChecklistResponse.ChecklistItem;
import com.gachi.be.domain.newsletter.dto.response.NewsletterListResponse.NewsletterItem;
import com.gachi.be.domain.newsletter.dto.response.NewsletterRecentResponse.DateGroup;
import com.gachi.be.domain.newsletter.dto.response.NewsletterRecentResponse.RecentItem;
import com.gachi.be.domain.newsletter.entity.ConversationTopic;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.NewsletterCulturalGuide;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import com.gachi.be.domain.newsletter.pipeline.NewsletterPipelineService;
import com.gachi.be.domain.newsletter.repository.ConversationTopicRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterCulturalGuideRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.newsletter.service.NewsletterService;
import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import com.gachi.be.domain.schoolguide.repository.SchoolGuideRepository;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.file.service.S3FileService;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import com.gachi.be.global.exception.ExternalApiException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 흐름: 파일 유효성 검사 → SHA-256 해시 계산 → 중복 확인 → S3 업로드 → 자녀 정보 스냅샷 조회 → newsletter DB 저장(PENDING)
 * childId 대신 child_name/child_grade/child_color 스냅샷 저장 중복 체크 기준: (user_id, child_name, file_hash)
 * 또는 (user_id, file_hash)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterServiceImpl implements NewsletterService {

  private final NewsletterRepository newsletterRepository;
  private final ChildRepository childRepository;
  private final S3FileService s3FileService;
  private final CalendarEventRepository calendarEventRepository;
  private final ChecklistRepository checklistRepository;
  private final NewsletterPipelineService newsletterPipelineService;
  private final UserRepository userRepository;
  private final ConversationTopicRepository conversationTopicRepository;
  private final NewsletterCulturalGuideRepository newsletterCulturalGuideRepository;
  private final SchoolGuideRepository schoolGuideRepository;
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int PAGE_SIZE = 20;
  private static final int MAX_FILE_COUNT = 10;
  private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L; // 장당 10MB
  private static final long MAX_TOTAL_SIZE_BYTES = 50 * 1024 * 1024L; // 전체 합계 50MB
  private static final String CONTENT_TYPE_JPEG = "image/jpeg";
  private static final String CONTENT_TYPE_PNG = "image/png";
  private static final String CONTENT_TYPE_PDF = "application/pdf";

  /**
   * 가정통신문 파일을 S3에 업로드하고 newsletter 레코드를 PENDING 상태로 생성한다.
   *
   * <p>처리 순서: 파일 유효성 검사 (형식: jpg/png/pdf, 크기: 최대 10MB) SHA-256 해시 계산 (중복 방지용) 중복 파일 확인 S3 업로드 →
   * file_key 획득 childId가 있으면 children 테이블에서 자녀 정보 조회 (스냅샷용) newsletter 레코드 DB 저장 (status=PENDING 으로
   * 변경) AI 분석 파이프라인 비동기 트리거 -> Asyncㅏ로 별도 스레드에서 실행하게 함.
   */
  @Override
  @Transactional
  public NewsletterUploadResponse upload(Long userId, List<MultipartFile> files, Long childId) {

    // 파일 유효성 검사
    validateFiles(files);

    // SHA-256 해시 계산
    // 여러 장의 내용을 배열 순서대로 이어서 하나의 해시값으로 계산
    // 1장만 올린 경우 기존(단일 파일) 해시값과 완전히 동일하므로,
    // 여러 장 기능 도입 전에 저장된 문서의 중복 판정이 그대로 유지된다.
    String fileHash = computeSha256(files);
    log.debug("[Newsletter] 해시 계산 완료. userId={}, fileHash={}", userId, fileHash);

    // 자녀 정보 조회 (스냅샷용)
    String childName = null;
    Integer childGrade = null;
    String childColor = null;

    if (childId != null) {
      // 해당 사용자 소유의 활성 자녀인지 확인
      Child child =
          childRepository
              .findByIdAndUserIdAndDeletedAtIsNull(childId, userId)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCode.INVALID_INPUT_VALUE, "존재하지 않는 자녀입니다. childId=" + childId));

      // 업로드 시점의 값을 복사 (이후 child 정보가 변경되어도 여기는 유지)
      childName = child.getName();
      childGrade = child.getGrade();
      childColor = child.getColorCode(); // 색상만 예외적으로 나중에 동기화 대상
    }

    // 중복 파일 확인
    // 자녀 특정 시: (user_id + child_name + file_hash) 조합으로 확인
    // 자녀 미선택 시: (user_id + file_hash) 조합으로 확인
    checkDuplicate(userId, childName, fileHash);

    // 프론트 언어처리로만 이루어지지 않고 설정한 언어를 자동으로 사용하게 변경
    String userLanguage = resolveUserLanguage(userId);
    log.debug("[Newsletter] 사용자 언어 설정 조회 완료. userId={}, language={}", userId, userLanguage);

    // S3 업로드 - 가정통신문 전용 경로에 저장 + 디버깅 로그 추가해서 체크
    List<String> fileKeys = uploadAllToS3(userId, files);

    return saveAndTriggerPipeline(
        userId, childName, childGrade, childColor, fileKeys, fileHash, userLanguage);
  }

  /**
   * 파일 목록을 배열 순서 그대로 S3에 업로드하고 file_key 목록을 반환한다.중간에 실패하면 이미 올라간 파일이 S3에 고아로 남으므로 즉시 정리한다. (DB 저장
   * 이후의 롤백 정리는 saveAndTriggerPipeline의 트랜잭션 동기화가 담당하지만, 이 시점은 아직 newsletter 레코드가 없어 별도 정리가 필요하다.)
   */
  private List<String> uploadAllToS3(Long userId, List<MultipartFile> files) {
    List<String> fileKeys = new ArrayList<>();
    try {
      for (MultipartFile file : files) {
        String fileKey = s3FileService.uploadNewsletter(file).key();
        fileKeys.add(fileKey);
        log.debug(
            "[Newsletter] S3 업로드 완료. userId={}, page={}/{}, fileKey={}",
            userId,
            fileKeys.size(),
            files.size(),
            fileKey);
      }
      return fileKeys;
    } catch (RuntimeException e) {
      log.error(
          "[Newsletter] S3 업로드 중 실패. 이미 업로드된 {}건을 정리합니다. userId={}, error={}",
          fileKeys.size(),
          userId,
          e.getMessage());
      deleteQuietly(fileKeys);
      throw e;
    }
  }

  /** S3 파일 목록을 삭제. 삭제 실패는 로그만 남기고 진행한다 (별도 정리 배치 가능). */
  private void deleteQuietly(List<String> fileKeys) {
    for (String fileKey : fileKeys) {
      try {
        s3FileService.deleteFile(fileKey);
      } catch (Exception ex) {
        log.error("[Newsletter] S3 파일 삭제 실패. fileKey={}, error={}", fileKey, ex.getMessage());
      }
    }
  }

  // userId로 users 테이블에서 language_code를 조회하는 내부 메서드.
  // 사용자를 찾지 못하면 기본값 'KO'를 반환한다 (방어 코드).
  private String resolveUserLanguage(Long userId) {
    return userRepository
        .findById(userId)
        .map(User::getLanguageCode)
        .filter(lang -> lang != null && !lang.isBlank())
        .orElseGet(
            () -> {
              log.warn("[Newsletter] 사용자 언어 조회 실패. userId={}. 기본값 KO 사용.", userId);
              return "KO";
            });
  }

  // file_key 컬럼에는 대표(첫장) 키를 그대로 저장해 기존 유니크 인덱스/조회 코드를 유지하고 전체 콕록은 file_keys(JSONB)에 페이지 순서대로 보관
  @Transactional
  protected NewsletterUploadResponse saveAndTriggerPipeline(
      Long userId,
      String childName,
      Integer childGrade,
      String childColor,
      List<String> fileKeys,
      String fileHash,
      String userLanguage) {
    String representativeFileKey = fileKeys.get(0);

    Newsletter newsletter =
        Newsletter.builder()
            .userId(userId)
            .childName(childName)
            .childGrade(childGrade)
            .childColor(childColor)
            .fileKey(representativeFileKey)
            .fileKeys(fileKeys)
            .fileHash(fileHash)
            .status(NewsletterStatus.PENDING)
            .language(userLanguage != null ? userLanguage : "KO")
            .build();

    Newsletter saved;
    try {
      // DataIntegrityViolationException → NEWSLETTER_DUPLICATE 변환
      // checkDuplicate()를 통과했더라도 동시에 두 요청이 들어오면
      // 한쪽이 DB 유니크 인덱스 위반으로 예외를 받을 수 있음
      saved = newsletterRepository.save(newsletter);
    } catch (DataIntegrityViolationException e) {
      log.warn("[Newsletter] DB 유니크 인덱스 위반 (동시 업로드). userId={}, fileHash={}", userId, fileHash);
      throw new BusinessException(ErrorCode.NEWSLETTER_DUPLICATE);
    }

    final Long savedId = saved.getId();
    final List<String> savedFileKeys = List.copyOf(fileKeys);
    log.info("[Newsletter] 업로드 완료. userId={}, newsletterId={}", userId, savedId);

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            // 트랜잭션 커밋 후 파이프라인 비동기 실행
            log.debug("[Newsletter] 트랜잭션 커밋. 파이프라인 트리거. newsletterId={}", savedId);
            newsletterPipelineService.runPipeline(savedId);
          }

          @Override
          public void afterCompletion(int status) {
            // 트랜잭션 롤백 시 S3 고아 파일 삭제
            if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
              // 업로드된 모든 장을 정리한다. 삭제 실패는 로그만 남기고 진행.
              log.warn("[Newsletter] 트랜잭션 롤백. S3 파일 정리. fileKeys={}", savedFileKeys);
              deleteQuietly(savedFileKeys);
            }
          }
        });

    return new NewsletterUploadResponse(saved.getId(), saved.getStatus());
  }

  /**
   * 가정통신문의 현재 분석 상태와 진행률을 반환. 프론트엔드 스캔 중 화면에서 2초마다 이 API를 호출(폴링)하여 진행률을 표시. TODO : 추후 AI 서버에서 단계별
   * 콜백을 받으면 세분화 시켜서 진행률 반환 현재는 상태별 고정 진행률로 반환
   */
  @Override
  @Transactional(readOnly = true)
  public NewsletterStatusResponse getStatus(Long userId, Long newsletterId) {
    Newsletter newsletter = findNewsletterById(newsletterId);
    validateOwnership(newsletter, userId);
    return NewsletterStatusResponse.of(newsletter);
  }

  /** 실패한 분석을 사용자가 다시 시도할 수 있도록 파생 데이터를 비우고 파이프라인을 재실행합니다. */
  @Override
  @Transactional
  public NewsletterUploadResponse retryAnalysis(Long userId, Long newsletterId) {
    Newsletter newsletter = findNewsletterById(newsletterId);
    validateOwnership(newsletter, userId);

    int updated = newsletterRepository.markRetryPendingIfFailed(newsletterId, userId);
    if (updated == 0) {
      throw new BusinessException(ErrorCode.NEWSLETTER_RETRY_NOT_ALLOWED);
    }

    checklistRepository.deleteByNewsletterId(newsletterId);
    calendarEventRepository.deleteByNewsletterIdAndUserId(newsletterId, userId);
    conversationTopicRepository.deleteByNewsletterId(newsletterId);
    newsletterCulturalGuideRepository.deleteByNewsletterId(newsletterId);
    Newsletter saved = findNewsletterById(newsletterId);

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            log.info("[Newsletter] 분석 재시도 파이프라인 트리거. newsletterId={}", newsletterId);
            newsletterPipelineService.runPipeline(newsletterId);
          }
        });

    return new NewsletterUploadResponse(saved.getId(), saved.getStatus());
  }

  /** 번역 결과 조회 */
  @Override
  @Transactional(readOnly = true)
  public NewsletterTranslationResponse getTranslation(Long userId, Long newsletterId) {
    Newsletter newsletter = findNewsletterById(newsletterId);
    validateOwnership(newsletter, userId);
    validateTextReadable(newsletter);

    String fileUrl = null;
    try {
      fileUrl = s3FileService.generatePresignedUrl(newsletter.getFileKey());
      log.debug("[Newsletter] Presigned URL 생성. newsletterId={}", newsletterId);
    } catch (ExternalApiException e) {
      log.warn(
          "[Newsletter] Presigned URL 생성 실패. newsletterId={}, fileKey={}",
          newsletterId,
          newsletter.getFileKey(),
          e);
    }
    return NewsletterTranslationResponse.from(newsletter, newsletter.getDateCandidates(), fileUrl);
  }

  /** 요약 결과 조회. 스캔 결과 [AI요약] 탭 상단의 요약문을 반환합니다. */
  @Override
  @Transactional(readOnly = true)
  public NewsletterSummaryResponse getSummary(Long userId, Long newsletterId) {
    Newsletter newsletter = findNewsletterById(newsletterId);
    validateOwnership(newsletter, userId);
    validateCompleted(newsletter);
    return NewsletterSummaryResponse.from(newsletter);
  }

  /** 체크리스트/해야할일 조회. type 파라미터로 반환 항목을 필터링 진행 */
  @Override
  @Transactional(readOnly = true)
  public NewsletterChecklistResponse getChecklist(Long userId, Long newsletterId, String type) {
    Newsletter newsletter = findNewsletterById(newsletterId);
    validateOwnership(newsletter, userId);
    validateCompleted(newsletter);

    // type 파라미터를 ChecklistType enum으로 변환
    // null이면 전체 조회, 잘못된 값이면 INVALID_INPUT_VALUE 에러
    ChecklistType filterType = parseChecklistType(type);

    // type에 따라 DB에서 항목 조회
    List<Checklist> checklists = fetchChecklists(newsletterId, filterType);

    // CHECKLIST 타입 항목의 dueDate 계산을 위해 연결된 CalendarEvent 조회
    // TODO 타입은 calendarEventId가 없으므로 CHECKLIST 항목의 eventId만 수집
    Map<Long, String> dueDateByEventId = buildDueDateMap(checklists);

    // 타입에 따라 적절한 팩토리 메서드로 DTO 변환
    List<ChecklistItem> items =
        checklists.stream().map(c -> toChecklistItem(c, dueDateByEventId)).toList();

    return NewsletterChecklistResponse.of(items);
  }

  /** type 문자열을 ChecklistType enum으로 변환 */
  private ChecklistType parseChecklistType(String type) {
    if (type == null) return null;

    try {
      return ChecklistType.valueOf(type.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE, "type은 CHECKLIST 또는 TODO만 허용됩니다. 입력값: " + type, e);
    }
  }

  /** filterType에 따라 체크리스트를 DB에서 조회합니다. */
  private List<Checklist> fetchChecklists(Long newsletterId, ChecklistType filterType) {
    if (filterType == null) {
      // 타입 필터 없음: CHECKLIST + TODO 전체 반환
      return checklistRepository.findByNewsletterIdOrderByIdAsc(newsletterId);
    } else {
      // 특정 타입만 반환
      return checklistRepository.findByNewsletterIdAndTypeOrderByIdAsc(newsletterId, filterType);
    }
  }

  /** CHECKLIST 타입 항목들의 calendarEventId → dueDate(KST YYYY-MM-DD) 맵을 생성 */
  private Map<Long, String> buildDueDateMap(List<Checklist> checklists) {
    // CHECKLIST 타입 중 calendarEventId가 있는 것만 수집 (중복 제거)
    List<Long> eventIds =
        checklists.stream()
            .filter(c -> c.getType() == ChecklistType.CHECKLIST)
            .map(Checklist::getCalendarEventId)
            .filter(id -> id != null)
            .distinct()
            .toList();

    if (eventIds.isEmpty()) return Map.of();

    // 한 번의 DB 조회로 모든 CalendarEvent 조회 (N+1 방지)
    return calendarEventRepository.findAllById(eventIds).stream()
        .collect(
            Collectors.toMap(
                CalendarEvent::getId,
                e ->
                    e.getStartAt()
                        .atZoneSameInstant(KST)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE)));
  }

  /** Checklist 엔티티를 타입에 맞는 ChecklistItem DTO로 변환합니다. */
  private ChecklistItem toChecklistItem(Checklist checklist, Map<Long, String> dueDateByEventId) {
    if (checklist.getType() == ChecklistType.CHECKLIST) {
      // CHECKLIST: dueDate를 eventId 맵에서 조회 (없으면 null = 캘린더 미등록)
      String dueDate =
          checklist.getCalendarEventId() != null
              ? dueDateByEventId.get(checklist.getCalendarEventId())
              : null;
      return ChecklistItem.ofChecklist(checklist, dueDate);
    } else {
      return ChecklistItem.ofTodo(checklist);
    }
  }

  /** 가정통신문 상세 조회. */
  @Override
  @Transactional(readOnly = true)
  public NewsletterDetailResponse getDetail(Long userId, Long newsletterId) {
    Newsletter newsletter = findNewsletterById(newsletterId);
    validateOwnership(newsletter, userId);

    // calendar_events에 해당 newsletter로 등록된 일정 존재 여부 확인
    boolean calendarRegistered =
        calendarEventRepository.existsByNewsletterIdAndUserId(newsletterId, userId);

    return NewsletterDetailResponse.from(newsletter, calendarRegistered);
  }

  /** 가정통신문 목록 조회. */
  @Override
  @Transactional(readOnly = true)
  public NewsletterListResponse getList(
      Long userId, String childName, String search, int page, String sort) {

    // sort 파라미터 → Pageable 생성
    // "recent" 또는 기타 값: createdAt 내림차순 (최신순)
    // "oldest": createdAt 오름차순
    Sort sortOrder =
        "oldest".equalsIgnoreCase(sort)
            ? Sort.by("createdAt").ascending()
            : Sort.by("createdAt").descending();

    Pageable pageable = PageRequest.of(page, PAGE_SIZE);

    // 동적 조건 조회: childName=null이면 전체, search=null이면 전체
    // 빈 문자열("")은 null로 변환해서 전체 조회로 처리
    String childNameFilter = (childName != null && childName.isBlank()) ? null : childName;
    String searchFilter = (search != null && search.isBlank()) ? null : search;

    Page<Newsletter> newsletterPage =
        newsletterRepository.findByUserIdWithFilters(
            userId, childNameFilter, searchFilter, sort, pageable);

    List<Newsletter> newsletters = newsletterPage.getContent();

    // N+1 방지: 조회된 newsletter ID 목록으로 캘린더 등록 여부를 한 번에 확인
    // → calendar_events에서 해당 newsletterId들 중 등록된 것들의 ID를 Set으로 조회
    Set<Long> registeredNewsletterIds = getRegisteredNewsletterIds(userId, newsletters);

    // DTO 변환
    List<NewsletterItem> items =
        newsletters.stream()
            .map(n -> NewsletterItem.from(n, registeredNewsletterIds.contains(n.getId())))
            .toList();

    return new NewsletterListResponse(items, (int) newsletterPage.getTotalElements());
  }

  /** 홈화면 최근 7일 가정통신문 조회. */
  @Override
  @Transactional(readOnly = true)
  public NewsletterRecentResponse getRecent(Long userId) {

    // KST 기준 오늘 날짜
    LocalDate todayKst = LocalDate.now(KST);

    OffsetDateTime rangeStart = todayKst.minusDays(6).atStartOfDay(KST).toOffsetDateTime();

    OffsetDateTime rangeEnd = todayKst.plusDays(1).atStartOfDay(KST).toOffsetDateTime();

    List<Newsletter> newsletters =
        newsletterRepository.findRecentByUserId(userId, rangeStart, rangeEnd);

    Map<String, List<Newsletter>> grouped =
        newsletters.stream()
            .collect(
                Collectors.groupingBy(
                    n ->
                        n.getCreatedAt()
                            .atZoneSameInstant(KST)
                            .toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE),
                    LinkedHashMap::new,
                    Collectors.toList()));

    // Map → DateGroup 목록으로 변환
    List<DateGroup> groups =
        grouped.entrySet().stream()
            .map(
                entry -> {
                  List<RecentItem> items = entry.getValue().stream().map(RecentItem::from).toList();
                  return new DateGroup(entry.getKey(), items);
                })
            .toList();

    return new NewsletterRecentResponse(groups);
  }

  @Override
  @Transactional(readOnly = true)
  public ConversationTopicResponse getConversationTopics(Long userId, Long newsletterId) {

    // 소유권 검증
    Newsletter newsletter =
        newsletterRepository
            .findById(newsletterId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND));

    if (!newsletter.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND);
    }

    List<ConversationTopic> topics =
        conversationTopicRepository.findAllByNewsletterIdOrderByIdAsc(newsletterId);

    return ConversationTopicResponse.from(topics);
  }

  // 문화 맥락 안내 조회.
  // AI 요약 탭 안에 노출되는 항목이므로, 캘린더 미등록 문서에서는 빈 배열을 반환.
  // 답변 텍스트는 school_guide DB 원문을 사용자 언어로 해석해서 그대로 내려준다 (추가 번역 없음).
  @Override
  @Transactional(readOnly = true)
  public NewsletterCulturalGuideResponse getCulturalGuides(Long userId, Long newsletterId) {

    Newsletter newsletter = findNewsletterById(newsletterId);
    validateOwnership(newsletter, userId);
    validateCompleted(newsletter);

    List<NewsletterCulturalGuide> mappings =
        newsletterCulturalGuideRepository.findAllByNewsletterIdOrderByDisplayOrderAsc(newsletterId);
    if (mappings.isEmpty()) {
      return new NewsletterCulturalGuideResponse(List.of());
    }

    // N+1 방지: faqId 목록으로 한 번에 조회한 뒤 Map으로 O(1) 매칭
    List<Long> faqIds =
        mappings.stream().map(NewsletterCulturalGuide::getSchoolGuideId).distinct().toList();
    Map<Long, SchoolGuide> faqById =
        schoolGuideRepository.findAllById(faqIds).stream()
            .collect(Collectors.toMap(SchoolGuide::getId, faq -> faq));

    // display_order 순서를 유지한 채 정렬. FAQ가 삭제된 경우는 건너뛴다.
    List<SchoolGuide> orderedFaqs =
        mappings.stream()
            .map(mapping -> faqById.get(mapping.getSchoolGuideId()))
            .filter(Objects::nonNull)
            .toList();

    String language = resolveUserLanguage(userId);
    return NewsletterCulturalGuideResponse.of(orderedFaqs, language);
  }

  /** 조회된 newsletter 목록에서 캘린더 등록된 newsletterId 집합을 반환 */
  private Set<Long> getRegisteredNewsletterIds(Long userId, List<Newsletter> newsletters) {
    if (newsletters.isEmpty()) return Set.of();

    // newsletter ID 목록 추출
    List<Long> newsletterIds = newsletters.stream().map(Newsletter::getId).toList();
    return new HashSet<>(
        calendarEventRepository.findRegisteredNewsletterIds(userId, newsletterIds));
  }

  /**
   * 파일 목록 유효성 검사.
   *
   * <p>TODO: 허용방식은 일단 이렇게만 지정해두고 테스트 해보면서 추가할 지 고려. 허용 형식: image/jpeg, image/png, application/pdf
   * 최대 크기: 10MB->합계 최대 50MB 최대 10장
   */
  private void validateFiles(List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      throw new BusinessException(ErrorCode.NEWSLETTER_FILE_EMPTY);
    }
    // 개수 초과는 개별 파일 검사보다 먼저 확인한다 (불필요한 순회 방지)
    if (files.size() > MAX_FILE_COUNT) {
      throw new BusinessException(
          ErrorCode.NEWSLETTER_FILE_COUNT_EXCEEDED, "업로드 장 수=" + files.size());
    }

    long totalSize = 0L;
    boolean containsPdf = false;

    for (int i = 0; i < files.size(); i++) {
      MultipartFile file = files.get(i);

      // 한 장이라도 비어 있으면 프론트 전송 누락일 가능성이 높으므로 조기에 실패시킨다.
      if (file == null || file.isEmpty()) {
        throw new BusinessException(ErrorCode.NEWSLETTER_FILE_EMPTY, "비어있는 파일 index=" + i);
      }

      String contentType = file.getContentType();
      boolean allowed =
          contentType != null
              && (contentType.equals(CONTENT_TYPE_JPEG)
                  || contentType.equals(CONTENT_TYPE_PNG)
                  || contentType.equals(CONTENT_TYPE_PDF));

      if (!allowed) {
        throw new BusinessException(
            ErrorCode.NEWSLETTER_FILE_TYPE_INVALID, "index=" + i + ", contentType=" + contentType);
      }

      if (file.getSize() > MAX_FILE_SIZE_BYTES) {
        throw new BusinessException(
            ErrorCode.NEWSLETTER_FILE_SIZE_EXCEEDED, "index=" + i + ", size=" + file.getSize());
      }

      if (CONTENT_TYPE_PDF.equals(contentType)) {
        containsPdf = true;
      }
      totalSize += file.getSize();
    }

    // PDF는 단독 1개만 허용 (이미지와 혼합 불가, PDF 여러 개 불가)
    if (containsPdf && files.size() > 1) {
      throw new BusinessException(
          ErrorCode.NEWSLETTER_FILE_MIXED_TYPE, "PDF 포함 상태로 " + files.size() + "개 전달됨");
    }

    if (totalSize > MAX_TOTAL_SIZE_BYTES) {
      throw new BusinessException(
          ErrorCode.NEWSLETTER_FILE_TOTAL_SIZE_EXCEEDED, "총 용량=" + totalSize + "bytes");
    }
  }

  /** 파일의 SHA-256 해시값 계산 */
  private String computeSha256(List<MultipartFile> files) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (MultipartFile file : files) {
        try (InputStream is = file.getInputStream()) {
          byte[] buffer = new byte[8192]; // 8KB 버퍼
          int bytesRead;
          while ((bytesRead = is.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
          }
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      // SHA-256은 Java 표준 알고리즘이라 실제로 발생하지 않음
      throw new RuntimeException("SHA-256 알고리즘 없음. JVM 환경 확인 필요.", e);
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.NEWSLETTER_FILE_READ_FAILED);
    }
  }

  /**
   * 동일 파일 중복 업로드 여부 확인. 중복 판단 기준 (DB Partial Unique Index와 동일) 자녀 특정: (user_id + child_name
   * +file_hash) 조합 자녀 미선택: (user_id + file_hash) 조합
   */
  private void checkDuplicate(Long userId, String childName, String fileHash) {
    boolean isDuplicate;

    if (childName != null) {
      // 자녀 특정: 같은 사용자의 같은 자녀에게 동일 파일이 이미 있는지 확인
      isDuplicate =
          newsletterRepository
              .findByUserIdAndChildNameAndFileHash(userId, childName, fileHash)
              .isPresent();
    } else {
      // 자녀 미선택: 같은 사용자가 자녀 미선택으로 동일 파일을 이미 올렸는지 확인
      isDuplicate =
          newsletterRepository
              .findByUserIdAndChildNameIsNullAndFileHash(userId, fileHash)
              .isPresent();
    }

    if (isDuplicate) {
      throw new BusinessException(ErrorCode.NEWSLETTER_DUPLICATE);
    }
  }

  /** newsletterId로 newsletter 레코드 조회. */
  private Newsletter findNewsletterById(Long newsletterId) {
    return newsletterRepository
        .findById(newsletterId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND));
  }

  /** 해당 가정통신문이 현재 사용자 소유인지 검증. */
  private void validateOwnership(Newsletter newsletter, Long userId) {
    if (!newsletter.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND);
    }
  }

  /** newsletter가 completed 상태인지 먼저 검증 -> for 결과 조회 */
  private void validateCompleted(Newsletter newsletter) {
    if (newsletter.getStatus() != NewsletterStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.NEWSLETTER_NOT_COMPLETED);
    }
  }

  private void validateTextReadable(Newsletter newsletter) {
    if (newsletter.getStatus() == NewsletterStatus.COMPLETED) {
      return;
    }
    if (newsletter.getStatus() == NewsletterStatus.FAILED
        && newsletter.getOriginalText() != null
        && !newsletter.getOriginalText().isBlank()) {
      return;
    }
    throw new BusinessException(ErrorCode.NEWSLETTER_NOT_COMPLETED);
  }
}
