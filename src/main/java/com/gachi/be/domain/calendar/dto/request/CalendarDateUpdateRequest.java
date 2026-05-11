package com.gachi.be.domain.calendar.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

// 복수 일정 동시 수정 가능 (배열로 전달)
public record CalendarDateUpdateRequest(
    @NotEmpty(message = "수정할 일정이 1개 이상 있어야 합니다.") @Valid List<EventDateUpdate> events) {

  /** 단건 날짜 수정 항목. */
  public record EventDateUpdate(
      @NotBlank(message = "tempEventId는 필수입니다.") String tempEventId,

      // 사용자가 직접 입력한 날짜. -> 등록 시 KST 00:00:00으로 처리됨
      @NotNull(message = "correctedDate는 필수입니다.")
          @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "날짜 형식은 YYYY-MM-DD 이어야 합니다.")
          String correctedDate) {}
}
