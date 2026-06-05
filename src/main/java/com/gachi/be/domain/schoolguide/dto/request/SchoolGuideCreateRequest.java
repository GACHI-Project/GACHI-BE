package com.gachi.be.domain.schoolguide.dto.request;

import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SchoolGuideCreateRequest {

  @NotNull(message = "카테고리는 필수입니다.")
  private SchoolGuideCategory category;

  @NotBlank(message = "질문은 필수입니다.")
  private String question;

  @NotBlank(message = "답변은 필수입니다.")
  private String answer;
}
