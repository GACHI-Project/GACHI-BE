package com.gachi.be.domain.calendar.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

// 복수 일정 동시 수정 가능 (배열로 전달)
public record CalendarDateUpdateRequest(
    @NotEmpty(message = "수정할 일정이 1개 이상 있어야 합니다.") @Valid List<EventDateUpdate> events) {

  /** 단건 날짜 수정 항목. */
  public record EventDateUpdate(
      @NotBlank(message = "tempEventId는 필수입니다.") String tempEventId,

      // 사용자가 직접 입력한 날짜. -> 등록 시 KST 00:00:00으로 처리됨
      @NotNull(message = "correctedDate는 필수입니다.")
      @JsonFormat(pattern = "yyyy-MM-dd")
      LocalDate correctedDate) {}
}
