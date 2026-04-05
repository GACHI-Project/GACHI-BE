# Error Code Table

| ERROR_CODE | HTTP_STATUS | message | 설명 | 로그레벨 |
|---|---|---|---|---|
| COMMON4001 | 400 BAD_REQUEST | 입력값이 올바르지 않습니다. | 요청 DTO/파라미터 검증 실패 | WARN |
| COMMON4002 | 405 METHOD_NOT_ALLOWED | 지원하지 않는 HTTP 메서드입니다. | 잘못된 메서드 요청 | WARN |
| BUS4001 | 400 BAD_REQUEST | 비즈니스 규칙 위반입니다. | 도메인 정책 위반 | WARN |
| COMMON4041 | 404 NOT_FOUND | 요청한 리소스를 찾을 수 없습니다. | 요청 경로에 해당하는 리소스 없음 | INFO |
| USER4041 | 404 NOT_FOUND | 사용자를 찾을 수 없습니다. | 식별자에 해당하는 사용자 없음 | INFO |
| AUTH4001 | 400 BAD_REQUEST | 이메일 인증이 완료되지 않았습니다. | 회원가입 전 이메일 인증 필요 | WARN |
| AUTH4002 | 400 BAD_REQUEST | 인증 코드가 일치하지 않습니다. | 인증 코드 불일치 | WARN |
| AUTH4003 | 400 BAD_REQUEST | 인증 코드가 만료되었습니다. | 인증 코드 TTL 만료 | WARN |
| AUTH4004 | 400 BAD_REQUEST | 비밀번호 확인 값이 일치하지 않습니다. | 회원가입 비밀번호 확인 불일치 | WARN |
| AUTH4005 | 400 BAD_REQUEST | 약관 및 개인정보 처리방침 동의가 필요합니다. | 회원가입 동의 미체크 | WARN |
| AUTH4011 | 401 UNAUTHORIZED | 아이디 또는 비밀번호가 올바르지 않습니다. | 로그인 인증 실패 | WARN |
| AUTH4012 | 401 UNAUTHORIZED | 유효하지 않은 리프레시 토큰입니다. | 리프레시 토큰 서명/형식 오류 | WARN |
| AUTH4013 | 401 UNAUTHORIZED | 리프레시 토큰이 만료되었습니다. | 리프레시 토큰 만료 | WARN |
| AUTH4014 | 401 UNAUTHORIZED | 철회된 리프레시 토큰입니다. | 로그아웃/회전으로 무효화된 토큰 | WARN |
| AUTH4031 | 403 FORBIDDEN | 탈퇴한 계정입니다. | 탈퇴 계정 로그인/토큰 발급 차단 | WARN |
| AUTH4091 | 409 CONFLICT | 이미 사용 중인 이메일입니다. | 회원가입 이메일 중복 | WARN |
| AUTH4092 | 409 CONFLICT | 이미 사용 중인 아이디입니다. | 회원가입 login_id 중복 | WARN |
| AUTH4093 | 409 CONFLICT | 이미 사용 중인 전화번호입니다. | 회원가입 전화번호 중복 | WARN |
| AUTH4291 | 429 TOO_MANY_REQUESTS | 인증 코드 재발송 대기 시간입니다. | 재발송 쿨타임 위반 | WARN |
| AUTH4292 | 429 TOO_MANY_REQUESTS | 인증 코드 입력 시도 횟수를 초과했습니다. | 인증 코드 최대 시도 초과 | WARN |
| EXT5021 | 502 BAD_GATEWAY | 외부 API 호출 중 오류가 발생했습니다. | 외부 연동 실패/타임아웃 | ERROR |
| COMMON5001 | 500 INTERNAL_SERVER_ERROR | 서버 내부 오류가 발생했습니다. | 처리되지 않은 예외 | ERROR |
