package com.gachi.be.domain.schoolguide.dto.request;

import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SchoolGuideUpdateRequest {

    private SchoolGuideCategory category;
    private String question;
    private String answer;
}
