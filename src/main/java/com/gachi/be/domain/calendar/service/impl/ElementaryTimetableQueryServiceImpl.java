package com.gachi.be.domain.calendar.service.impl;

import com.gachi.be.domain.calendar.dto.response.ElementaryTimetableCalendarResponse;
import com.gachi.be.domain.calendar.service.ElementaryTimetableQueryService;
import com.gachi.be.domain.calendar.service.impl.SchoolScheduleChildReader.SchoolScheduleChild;
import com.gachi.be.domain.school.client.NeisElementaryTimetableClient;
import com.gachi.be.domain.school.dto.response.NeisElementaryTimetableItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ElementaryTimetableQueryServiceImpl implements ElementaryTimetableQueryService {
  private static final long MAX_TIMETABLE_RANGE_DAYS = 366L;

  private final SchoolScheduleChildReader schoolScheduleChildReader;
  private final NeisElementaryTimetableClient neisElementaryTimetableClient;

  @Override
  public ElementaryTimetableCalendarResponse getElementaryTimetables(
      Long userId, LocalDate fromDate, LocalDate toDate) {
    validateRange(fromDate, toDate);

    Map<SchoolIdentity, List<SchoolScheduleChild>> childrenBySchool =
        groupBySchoolIdentity(schoolScheduleChildReader.findChildren(userId));
    List<ElementaryTimetableCalendarResponse.TimetableGroup> groups = new ArrayList<>();
    for (Map.Entry<SchoolIdentity, List<SchoolScheduleChild>> entry : childrenBySchool.entrySet()) {
      SchoolIdentity identity = entry.getKey();
      List<SchoolScheduleChild> schoolChildren = entry.getValue();
      List<NeisElementaryTimetableItem> timetables =
          schoolChildren.stream()
              .map(SchoolScheduleChild::grade)
              .filter(Objects::nonNull)
              .distinct()
              .flatMap(
                  grade ->
                      neisElementaryTimetableClient
                          .search(
                              identity.officeCode(), identity.schoolCode(), fromDate, toDate, grade)
                          .stream())
              .sorted(timetableComparator())
              .toList();
      groups.add(toGroup(identity, schoolChildren, timetables));
    }

    return new ElementaryTimetableCalendarResponse(groups);
  }

  private void validateRange(LocalDate fromDate, LocalDate toDate) {
    if (toDate.isBefore(fromDate)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "종료일은 시작일보다 빠를 수 없습니다.");
    }
    long requestedDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
    if (requestedDays > MAX_TIMETABLE_RANGE_DAYS) {
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

  private ElementaryTimetableCalendarResponse.TimetableGroup toGroup(
      SchoolIdentity identity,
      List<SchoolScheduleChild> children,
      List<NeisElementaryTimetableItem> timetables) {
    List<Long> childIds = children.stream().map(SchoolScheduleChild::childId).toList();
    List<ElementaryTimetableCalendarResponse.ChildItem> childItems =
        children.stream()
            .map(
                child ->
                    new ElementaryTimetableCalendarResponse.ChildItem(
                        child.childId(), child.childName(), child.grade(), child.colorCode()))
            .toList();
    List<ElementaryTimetableCalendarResponse.TimetableItem> timetableItems =
        timetables.stream().map(this::toTimetableItem).toList();

    return new ElementaryTimetableCalendarResponse.TimetableGroup(
        identity.groupKey(),
        identity.officeCode(),
        identity.schoolCode(),
        children.get(0).schoolName(),
        childIds,
        childItems,
        timetableItems);
  }

  private ElementaryTimetableCalendarResponse.TimetableItem toTimetableItem(
      NeisElementaryTimetableItem item) {
    return new ElementaryTimetableCalendarResponse.TimetableItem(
        item.date().format(DateTimeFormatter.ISO_LOCAL_DATE),
        item.academicYear(),
        item.semester(),
        item.grade(),
        item.className(),
        item.period(),
        item.content());
  }

  private Comparator<NeisElementaryTimetableItem> timetableComparator() {
    return Comparator.comparing(NeisElementaryTimetableItem::date)
        .thenComparing(item -> nullSafe(item.grade()))
        .thenComparing(item -> nullSafe(item.className()))
        .thenComparing(item -> nullSafe(item.period()));
  }

  private int nullSafe(Integer value) {
    return value == null ? Integer.MAX_VALUE : value;
  }

  private String nullSafe(String value) {
    return value == null ? "" : value;
  }

  private record SchoolIdentity(String officeCode, String schoolCode) {
    String groupKey() {
      return officeCode + ":" + schoolCode;
    }
  }
}
