package com.gachi.be.domain.calendar.service.impl;

import com.gachi.be.domain.calendar.dto.response.SchoolScheduleCalendarResponse;
import com.gachi.be.domain.calendar.service.SchoolScheduleQueryService;
import com.gachi.be.domain.child.entity.Child;
import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.school.client.NeisSchoolScheduleClient;
import com.gachi.be.domain.school.dto.response.NeisSchoolScheduleItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SchoolScheduleQueryServiceImpl implements SchoolScheduleQueryService {
  private final ChildRepository childRepository;
  private final NeisSchoolScheduleClient neisSchoolScheduleClient;

  @Override
  @Transactional(readOnly = true)
  public SchoolScheduleCalendarResponse getSchoolSchedules(
      Long userId, LocalDate fromDate, LocalDate toDate) {
    validateRange(fromDate, toDate);

    List<Child> children = childRepository.findByUserIdAndDeletedAtIsNull(userId);
    if (children.isEmpty()) {
      throw new BusinessException(ErrorCode.CHILD_NOT_FOUND);
    }

    Map<SchoolIdentity, List<Child>> childrenBySchool = groupBySchoolIdentity(children);
    List<SchoolScheduleCalendarResponse.SchoolScheduleGroup> groups = new ArrayList<>();
    for (Map.Entry<SchoolIdentity, List<Child>> entry : childrenBySchool.entrySet()) {
      SchoolIdentity identity = entry.getKey();
      List<Child> schoolChildren = entry.getValue();
      List<NeisSchoolScheduleItem> schedules =
          neisSchoolScheduleClient.search(
              identity.officeCode(), identity.schoolCode(), fromDate, toDate);
      groups.add(toGroup(identity, schoolChildren, schedules));
    }

    return new SchoolScheduleCalendarResponse(groups);
  }

  private void validateRange(LocalDate fromDate, LocalDate toDate) {
    if (toDate.isBefore(fromDate)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "종료일은 시작일보다 빠를 수 없습니다.");
    }
  }

  private Map<SchoolIdentity, List<Child>> groupBySchoolIdentity(List<Child> children) {
    Map<SchoolIdentity, List<Child>> grouped = new LinkedHashMap<>();
    for (Child child : children) {
      if (!StringUtils.hasText(child.getOfficeCode())
          || !StringUtils.hasText(child.getSchoolCode())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "자녀 학교의 교육청 코드와 학교 코드가 필요합니다.");
      }
      SchoolIdentity identity = new SchoolIdentity(child.getOfficeCode(), child.getSchoolCode());
      grouped.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(child);
    }
    return grouped;
  }

  private SchoolScheduleCalendarResponse.SchoolScheduleGroup toGroup(
      SchoolIdentity identity, List<Child> children, List<NeisSchoolScheduleItem> schedules) {
    List<Long> childIds = children.stream().map(Child::getId).toList();
    List<SchoolScheduleCalendarResponse.ChildItem> childItems =
        children.stream()
            .map(
                child ->
                    new SchoolScheduleCalendarResponse.ChildItem(
                        child.getId(), child.getName(), child.getGrade(), child.getColorCode()))
            .toList();
    List<SchoolScheduleCalendarResponse.ScheduleItem> scheduleItems =
        schedules.stream().map(this::toScheduleItem).toList();

    return new SchoolScheduleCalendarResponse.SchoolScheduleGroup(
        identity.groupKey(),
        identity.officeCode(),
        identity.schoolCode(),
        children.get(0).getSchoolName(),
        childIds,
        childItems,
        scheduleItems);
  }

  private SchoolScheduleCalendarResponse.ScheduleItem toScheduleItem(NeisSchoolScheduleItem item) {
    NeisSchoolScheduleItem.GradeEventYn gradeEventYn = item.gradeEventYn();
    return new SchoolScheduleCalendarResponse.ScheduleItem(
        item.date().format(DateTimeFormatter.ISO_LOCAL_DATE),
        item.academicYear(),
        item.eventName(),
        item.eventContent(),
        new SchoolScheduleCalendarResponse.GradeEventYn(
            gradeEventYn.grade1(),
            gradeEventYn.grade2(),
            gradeEventYn.grade3(),
            gradeEventYn.grade4(),
            gradeEventYn.grade5(),
            gradeEventYn.grade6()));
  }

  private record SchoolIdentity(String officeCode, String schoolCode) {
    String groupKey() {
      return officeCode + ":" + schoolCode;
    }
  }
}
