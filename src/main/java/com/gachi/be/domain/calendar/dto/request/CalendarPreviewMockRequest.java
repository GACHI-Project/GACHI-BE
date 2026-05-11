package com.gachi.be.domain.calendar.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

public record CalendarPreviewMockRequest(
    @NotEmpty(message = "events는 1개 이상이어야 합니다.") @Valid List<MockEvent> events) {

  public record MockEvent(

      /** 일정 제목. */
      @NotBlank(message = "title은 필수입니다.") String title,

      /** 일정 날짜. YYYY-MM-DD 형식. null이면 isDateExtracted=false로 저장 (사용자가 팝업에서 직접 입력). */
      @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "날짜 형식은 YYYY-MM-DD이어야 합니다.")
          String extractedDate,

      /** 이 일정에 연결할 체크리스트 ID 목록. null 또는 빈 리스트이면 해당 newsletter의 CHECKLIST 항목을 자동 균등 배분. */
      List<Long> checklistIds) {
    @AssertTrue(message = "유효한 날짜가 아닙니다. YYYY-MM-DD 형식의 실존 날짜를 입력해주세요.")
    public boolean isValidExtractedDate() {
      if (extractedDate == null) return true;
      try {
        LocalDate.parse(extractedDate);
        return true;
      } catch (DateTimeParseException e) {
        return false;
      }
    }
  }
}
