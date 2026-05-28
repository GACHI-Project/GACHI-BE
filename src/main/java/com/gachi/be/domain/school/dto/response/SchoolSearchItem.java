package com.gachi.be.domain.school.dto.response;

public record SchoolSearchItem(
    String schoolCode,
    String schoolName,
    String schoolKind,
    String officeCode,
    String officeName,
    String locationName,
    String roadAddress) {}
