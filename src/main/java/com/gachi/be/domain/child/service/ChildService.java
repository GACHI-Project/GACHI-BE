package com.gachi.be.domain.child.service;

import com.gachi.be.domain.auth.service.AuthenticatedUserResolver;
import com.gachi.be.domain.child.dto.request.ChildCreateRequest;
import com.gachi.be.domain.child.dto.response.ChildResponse;
import com.gachi.be.domain.child.entity.Child;
import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 자녀 등록/조회 비즈니스 로직을 담당한다. */
@Service
@RequiredArgsConstructor
public class ChildService {
  private static final long DEFAULT_MAX_CHILDREN_PER_USER = 20L;

  private final ChildRepository childRepository;
  private final AuthenticatedUserResolver authenticatedUserResolver;

  @Transactional
  public ChildResponse createChild(String authorizationHeader, ChildCreateRequest request) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    long childrenCount = childRepository.countByUserId(user.getId());
    if (childrenCount >= DEFAULT_MAX_CHILDREN_PER_USER) {
      // 정책은 무제한처럼 보이더라도 서버 보호를 위해 내부 가드레일을 둔다.
      throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    Child child =
        Child.builder()
            .user(user)
            .name(normalizeRequiredText(request.name()))
            .schoolName(normalizeRequiredText(request.schoolName()))
            .schoolCode(normalizeOptionalText(request.schoolCode()))
            .grade(request.grade())
            .colorCode(normalizeRequiredText(request.colorCode()).toUpperCase())
            .build();

    Child saved = childRepository.save(child);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<ChildResponse> getChildren(String authorizationHeader) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    return childRepository.findAllByUserIdOrderByCreatedAtAsc(user.getId()).stream()
        .map(this::toResponse)
        .toList();
  }

  private ChildResponse toResponse(Child child) {
    return new ChildResponse(
        child.getId(),
        child.getName(),
        child.getSchoolName(),
        child.getSchoolCode(),
        child.getGrade(),
        child.getColorCode(),
        child.getCreatedAt());
  }

  private String normalizeRequiredText(String value) {
    return value == null ? "" : value.trim();
  }

  private String normalizeOptionalText(String value) {
    String trimmed = normalizeRequiredText(value);
    return StringUtils.hasText(trimmed) ? trimmed : null;
  }
}
