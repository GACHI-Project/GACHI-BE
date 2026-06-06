package com.gachi.be.domain.school.dto.response;

import java.time.LocalDate;

public record NeisElementaryTimetableItem(
    String academicYear,
    String semester,
    LocalDate date,
    Integer grade,
    String className,
    Integer period,
    String content) {}
