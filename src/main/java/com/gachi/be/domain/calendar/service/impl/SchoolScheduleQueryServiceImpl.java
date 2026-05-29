package com.gachi.be.domain.calendar.service.impl;

import com.gachi.be.domain.calendar.dto.response.SchoolScheduleCalendarResponse;
import com.gachi.be.domain.calendar.service.SchoolScheduleQueryService;
import com.gachi.be.domain.calendar.service.impl.SchoolScheduleChildReader.SchoolScheduleChild;
import com.gachi.be.domain.school.client.NeisSchoolScheduleClient;
import com.gachi.be.domain.school.dto.response.NeisSchoolScheduleItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SchoolScheduleQueryServiceImpl implements SchoolScheduleQueryService {
  private static final long MAX_SCHOOL_SCHEDULE_RANGE_DAYS = 366L;

  private final SchoolScheduleChildReader schoolScheduleChildReader;
  private final NeisSchoolScheduleClient neisSchoolScheduleClient;

  @Override
  public SchoolScheduleCalendarResponse getSchoolSchedules(
      Long userId, LocalDate fromDate, LocalDate toDate) {
    validateRange(fromDate, toDate);

    Map<SchoolIdentity, List<SchoolScheduleChild>> childrenBySchool =
        groupBySchoolIdentity(schoolScheduleChildReader.findChildren(userId));
    List<SchoolScheduleCalendarResponse.SchoolScheduleGroup> groups = new ArrayList<>();
    for (Map.Entry<SchoolIdentity, List<SchoolScheduleChild>> entry : childrenBySchool.entrySet()) {
      SchoolIdentity identity = entry.getKey();
      List<SchoolScheduleChild> schoolChildren = entry.getValue();
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
    if (ChronoUnit.DAYS.between(fromDate, toDate) > MAX_SCHOOL_SCHEDULE_RANGE_DAYS) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "최대 1년까지 조회 가능합니다.");
    }
  }

  private Map<SchoolIdentity, List<SchoolScheduleChild>> groupBySchoolIdentity(
      List<SchoolScheduleChild> children) {
    Map<SchoolIdentity, List<SchoolScheduleChild>> grouped = new LinkedHashMap<>();
    for (SchoolScheduleChild child : children) {
      if (!StringUtils.hasText(child.officeCode()) || !StringUtils.hasText(child.schoolCode())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "자녀 학교의 교육청 코드와 학교 코드가 필요합니다.");
      }
      SchoolIdentity identity = new SchoolIdentity(child.officeCode(), child.schoolCode());
      grouped.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(child);
    }
    return grouped;
  }

  private SchoolScheduleCalendarResponse.SchoolScheduleGroup toGroup(
      SchoolIdentity identity,
      List<SchoolScheduleChild> children,
      List<NeisSchoolScheduleItem> schedules) {
    List<Long> childIds = children.stream().map(SchoolScheduleChild::childId).toList();
    List<SchoolScheduleCalendarResponse.ChildItem> childItems =
        children.stream()
            .map(
                child ->
                    new SchoolScheduleCalendarResponse.ChildItem(
                        child.childId(), child.childName(), child.grade(), child.colorCode()))
            .toList();
    List<SchoolScheduleCalendarResponse.ScheduleItem> scheduleItems =
        schedules.stream().map(this::toScheduleItem).toList();

    return new SchoolScheduleCalendarResponse.SchoolScheduleGroup(
        identity.groupKey(),
        identity.officeCode(),
        identity.schoolCode(),
        children.get(0).schoolName(),
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
