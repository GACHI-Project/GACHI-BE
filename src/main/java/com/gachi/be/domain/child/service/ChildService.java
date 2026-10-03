package com.gachi.be.domain.child.service;

import com.gachi.be.domain.auth.service.AuthenticatedUserResolver;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.child.dto.request.ChildCreateRequest;
import com.gachi.be.domain.child.dto.request.ChildUpdateRequest;
import com.gachi.be.domain.child.dto.response.ChildResponse;
import com.gachi.be.domain.child.entity.Child;
import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.NewsletterPage;
import com.gachi.be.domain.newsletter.repository.NewsletterPageRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.file.service.S3FileService;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/** 자녀 등록/조회 비즈니스 로직을 담당한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildService {
  private static final long DEFAULT_MAX_CHILDREN_PER_USER = 20L;

  private final ChildRepository childRepository;
  private final AuthenticatedUserResolver authenticatedUserResolver;
  private final NewsletterRepository newsletterRepository;
  private final CalendarEventRepository calendarEventRepository;
  private final S3FileService s3FileService;
  private final NewsletterPageRepository newsletterPageRepository;

  @Transactional
  public ChildResponse createChild(String authorizationHeader, ChildCreateRequest request) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    long childrenCount = childRepository.countByUserIdAndDeletedAtIsNull(user.getId());
    if (childrenCount >= DEFAULT_MAX_CHILDREN_PER_USER) {
      // 정책은 무제한처럼 보이더라도 서버 보호를 위해 내부 가드레일을 둔다.
      throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    Child child =
        Child.builder()
            .user(user)
            .name(normalizeRequiredText(request.name()))
            .schoolName(normalizeRequiredText(request.schoolName()))
            .schoolCode(normalizeRequiredText(request.schoolCode()))
            .officeCode(normalizeRequiredText(request.officeCode()))
            .grade(request.grade())
            .className(normalizeRequiredText(request.className()))
            .colorCode(normalizeRequiredText(request.colorCode()).toUpperCase())
            .build();

    Child saved = childRepository.save(child);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<ChildResponse> getChildren(String authorizationHeader) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    return childRepository
        .findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(user.getId())
        .stream()
        .map(this::toResponse)
        .toList();
  }

  private ChildResponse toResponse(Child child) {
    return new ChildResponse(
        child.getId(),
        child.getName(),
        child.getSchoolName(),
        child.getSchoolCode(),
        child.getOfficeCode(),
        child.getGrade(),
        child.getClassName(),
        child.getColorCode(),
        child.getCreatedAt());
  }

  // 자녀 정보 수정
  @Transactional
  public void updateChild(String authorizationHeader, Long childId, ChildUpdateRequest request) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);

    // 소유권 검증 (soft delete된 자녀는 수정 불가)
    Child child =
        childRepository
            .findByIdAndUserIdAndDeletedAtIsNull(childId, user.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CHILD_NOT_FOUND));

    String oldName = child.getName();
    String oldColorCode = child.getColorCode();
    String newName = request.name() != null ? request.name().trim() : null;
    String newColorCode =
        request.colorCode() != null ? request.colorCode().trim().toUpperCase() : null;

    child.update(
        newName,
        request.schoolName() != null ? request.schoolName().trim() : null,
        request.schoolCode() != null ? request.schoolCode().trim() : null,
        request.officeCode() != null ? request.officeCode().trim() : null,
        request.grade(),
        normalizeOptionalText(request.className()),
        newColorCode);

    // 이름이 변경된 경우 → newsletter, calendar_events child_name 일괄 동기화
    if (newName != null && !newName.equals(oldName)) {
      newsletterRepository.updateChildNameByUserIdAndOldName(user.getId(), oldName, newName);
      calendarEventRepository.updateChildNameByUserIdAndOldName(user.getId(), oldName, newName);
      log.debug(
          "[Child] child_name 동기화 완료. userId={}, oldName={}, newName={}",
          user.getId(),
          oldName,
          newName);
    }

    // 색상이 변경된 경우 → newsletter, calendar_events child_color 일괄 동기화
    if (newColorCode != null && !newColorCode.equals(oldColorCode)) {
      String syncTargetName = (newName != null) ? newName : oldName;
      newsletterRepository.updateChildColorByUserIdAndChildName(
          user.getId(), syncTargetName, newColorCode);
      calendarEventRepository.updateChildColorByUserIdAndChildName(
          user.getId(), syncTargetName, newColorCode);
      log.debug(
          "[Child] child_color 동기화 완료. userId={}, childName={}, newColor={}",
          user.getId(),
          syncTargetName,
          newColorCode);
    }
  }

  // 자녀 삭제 - 연관된 모든 데이터 삭제
  @Transactional
  public void deleteChild(String authorizationHeader, Long childId) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);

    // 소유권 검증
    Child child =
        childRepository
            .findByIdAndUserIdAndDeletedAtIsNull(childId, user.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CHILD_NOT_FOUND));

    String childName = child.getName();
    Long userId = user.getId();

    // 해당 자녀의 newsletter 목록 조회 → S3 fileKey 수집
    List<Newsletter> newsletters =
        newsletterRepository.findAllByUserIdAndChildName(userId, childName);
    // 대표 키(file_key)만 지우던 것을 문서의 모든 관련 S3 파일로 확장
    //   가정통신문에는 자녀 이름·학교 등 개인정보가 담겨 있어, DB만 지우고 S3 파일을 남기면 안 된다.
    //   newsletter_page 레코드는 FK ON DELETE CASCADE로 지워지므로, DB 삭제 전에 키를 먼저 모아둔다.
    List<String> fileKeys = collectS3FileKeys(newsletters);
    calendarEventRepository.deleteAllByUserIdAndChildName(userId, childName);
    log.debug("[Child] calendar_events 삭제 완료. userId={}, childName={}", userId, childName);

    newsletterRepository.deleteAllByUserIdAndChildName(userId, childName);
    log.debug(
        "[Child] newsletter 삭제 완료. userId={}, childName={}, count={}",
        userId,
        childName,
        newsletters.size());

    // 자녀 soft delete
    child.softDelete();
    log.info("[Child] 자녀 삭제 완료. userId={}, childId={}, childName={}", userId, childId, childName);

    // S3 삭제를 트랜잭션 커밋 이후 실행으로 분리
    // 트랜잭션 내부에서 외부 네트워크 호출 시 느린 S3 응답이 DB 커넥션을 불필요하게 점유하고,
    // S3 장애가 DB 롤백을 유발하는 리스크를 제거하기 위해 afterCommit으로 분리
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            for (String fileKey : fileKeys) {
              try {
                s3FileService.deleteFile(fileKey);
              } catch (Exception e) {
                // S3 삭제 실패는 로그만 남기고 계속 진행 (DB 삭제가 이미 커밋됐으므로 롤백 불가)
                log.error("[Child] S3 파일 삭제 실패. fileKey={}, error={}", fileKey, e.getMessage());
              }
            }
            log.debug(
                "[Child] S3 파일 삭제 완료. userId={}, childName={}, count={}",
                userId,
                childName,
                fileKeys.size());
          }
        });
  }

  /**
   * 삭제할 가정통신문들의 S3 키를 모두 모은다. 원본(대표 키 + 여러 장 키)과 페이지별 표시용 이미지 키를 합치고, 중복은 제거한다. (회전이 없는 페이지는
   * display_file_key가 원본 키와 같으므로 중복 삭제 요청이 생기지 않게 한다.)
   */
  private List<String> collectS3FileKeys(List<Newsletter> newsletters) {
    Set<String> keys = new LinkedHashSet<>();
    for (Newsletter newsletter : newsletters) {
      keys.add(newsletter.getFileKey());
      keys.addAll(newsletter.resolveFileKeys());
    }

    List<Long> newsletterIds = newsletters.stream().map(Newsletter::getId).toList();
    if (!newsletterIds.isEmpty()) {
      newsletterPageRepository.findAllByNewsletterIdIn(newsletterIds).stream()
          .map(NewsletterPage::getDisplayFileKey)
          .forEach(keys::add);
    }

    keys.removeIf(Objects::isNull);
    return List.copyOf(keys);
  }

  private String normalizeRequiredText(String value) {
    return value == null ? "" : value.trim();
  }

  private String normalizeOptionalText(String value) {
    String trimmed = normalizeRequiredText(value);
    return StringUtils.hasText(trimmed) ? trimmed : null;
  }
}
