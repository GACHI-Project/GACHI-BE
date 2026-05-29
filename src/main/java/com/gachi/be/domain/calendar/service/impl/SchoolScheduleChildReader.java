package com.gachi.be.domain.calendar.service.impl;

import com.gachi.be.domain.child.entity.Child;
import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class SchoolScheduleChildReader {
  private final ChildRepository childRepository;

  @Transactional(readOnly = true)
  List<SchoolScheduleChild> findChildren(Long userId) {
    List<Child> children = childRepository.findByUserIdAndDeletedAtIsNull(userId);
    if (children.isEmpty()) {
      throw new BusinessException(ErrorCode.CHILD_NOT_FOUND);
    }

    return children.stream()
        .map(
            child ->
                new SchoolScheduleChild(
                    child.getId(),
                    child.getName(),
                    child.getSchoolName(),
                    child.getSchoolCode(),
                    child.getOfficeCode(),
                    child.getGrade(),
                    child.getColorCode()))
        .toList();
  }

  record SchoolScheduleChild(
      Long childId,
      String childName,
      String schoolName,
      String schoolCode,
      String officeCode,
      Integer grade,
      String colorCode) {}
}
