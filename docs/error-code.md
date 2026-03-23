# Error Code Table

| ERROR_CODE | HTTP_STATUS | message | 설명 | 로그레벨 |
|---|---|---|---|---|
| COMMON4001 | 400 BAD_REQUEST | 입력값이 올바르지 않습니다. | 요청 DTO/파라미터 검증 실패 | WARN |
| COMMON4002 | 405 METHOD_NOT_ALLOWED | 지원하지 않는 HTTP 메서드입니다. | 잘못된 메서드 요청 | WARN |
| BUS4001 | 400 BAD_REQUEST | 비즈니스 규칙 위반입니다. | 도메인 정책 위반 | WARN |
| USER4041 | 404 NOT_FOUND | 사용자를 찾을 수 없습니다. | 식별자에 해당하는 사용자 없음 | INFO |
| EXT5021 | 502 BAD_GATEWAY | 외부 API 호출 중 오류가 발생했습니다. | 외부 연동 실패/타임아웃 | ERROR |
| COMMON5001 | 500 INTERNAL_SERVER_ERROR | 서버 내부 오류가 발생했습니다. | 처리되지 않은 예외 | ERROR |
