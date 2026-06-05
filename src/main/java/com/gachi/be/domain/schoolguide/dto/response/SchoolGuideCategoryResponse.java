package com.gachi.be.domain.schoolguide.dto.response;

import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SchoolGuideCategoryResponse {

  private List<CategoryItem> categories;

  public static SchoolGuideCategoryResponse of(List<CategoryItem> categories) {
    return SchoolGuideCategoryResponse.builder().categories(categories).build();
  }

  @Getter
  @Builder
  public static class CategoryItem {
    private SchoolGuideCategory category;
    private long count;

    public static CategoryItem of(SchoolGuideCategory category, long count) {
      return CategoryItem.builder().category(category).count(count).build();
    }
  }
}
