package com.gachi.be.domain.calendar.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 캘린더 일정 등록할 때 사용 */
// 프론트는 Redis preview 데이터를 기반으로 요청 보냄
public record CalendarRegisterRequest(
    @NotEmpty(message = "등록할 일정이 1개 이상 있어야 합니다.") @Valid List<EventRegister> events) {

  public record EventRegister(

      // preview에서 받은 임시 ID. external_key 생성에 사용 (중복 등록 방지).
      @NotBlank(message = "tempEventId는 필수입니다.") String tempEventId,
      @NotBlank(message = "일정 제목은 필수입니다.") String title,
      @NotBlank(message = "startAt은 필수입니다.") String startAt,

      // 기간 일정만 사용. null 허용.
      String endAt) {}
}
