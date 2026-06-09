package com.gachi.be.domain.child.dto.response;

import java.time.LocalDateTime;

public record ChildResponse(
    Long id,
    String name,
    String schoolName,
    String schoolCode,
    String officeCode,
    Integer grade,
    String className,
    String colorCode,
    LocalDateTime createdAt) {}
