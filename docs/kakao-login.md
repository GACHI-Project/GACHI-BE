# 카카오 로그인 BE 연동

## 흐름

1. 클라이언트가 `GET /api/v1/auth/kakao/authorize`를 연다.
2. BE가 카카오 인가 화면으로 리다이렉트하고, 콜백에서 `state`를 한 번만 검증한다.
3. BE가 인가 코드를 카카오 토큰으로 교환하고 사용자 정보를 조회한 뒤 앱 딥링크로 일회용 `ticket`만 전달한다.
4. 클라이언트가 `POST /api/v1/auth/kakao/complete`로 ticket을 교환한다.
   - 연동 회원: GACHI access/refresh token 반환
   - 신규 회원: `SIGNUP_REQUIRED`와 일회용 `signupToken` 반환
   - 같은 이메일의 로컬 회원: 자동 병합하지 않고 `LINK_REQUIRED`와 일회용 `linkToken` 반환
5. 신규 회원은 `POST /api/v1/auth/kakao/signup`에서 서비스 약관, 언어, 알림 설정만 제출한다. 아이디, 비밀번호, 전화번호 입력은 받지 않는다.

## API

- `GET /api/v1/auth/kakao/authorize`
- `GET /api/v1/auth/kakao/callback?code=...&state=...`
- `POST /api/v1/auth/kakao/complete`
- `POST /api/v1/auth/kakao/signup`
- `POST /api/v1/auth/kakao/link` (GACHI Bearer token 필요)
- `DELETE /api/v1/auth/kakao/link` (GACHI Bearer token 필요)
- `POST /api/v1/auth/kakao/unlink-webhook`

`state`, `ticket`, `signupToken`, `linkToken`은 Redis에 TTL과 함께 저장되고 조회 시 즉시 삭제된다. REST API 키는 공개 식별자인 OAuth `client_id`로 인가 URL에 포함된다. 반면 Client Secret, Admin Key, 카카오 access/refresh token은 URL이나 로그에 노출하지 않고 저장소, DB, Redis에도 저장하지 않는다.

## 카카오 콘솔 설정

- 카카오 로그인: ON
- Client Secret: 발급 후 ON
- Redirect URI: 배포 환경의 `${KAKAO_REDIRECT_URI}`와 정확히 일치
- 동의항목: 닉네임 및 인증된 카카오계정 이메일
- 전화번호: 사용할 수 있으면 `phone_number` 동의항목 추가. 값이 없는 사용자도 있으므로 서비스에서는 선택값으로 취급
- 연결 끊기 웹훅: `POST {BE_BASE_URL}/api/v1/auth/kakao/unlink-webhook`

필요 환경변수 이름은 `.env.example`과 `deploy/.env.example`을 참고한다. 실제 키는 저장소에 커밋하지 않는다.

## 연결 해제 정책

- 로컬 로그인 수단이 있는 회원: 카카오 연동만 삭제
- 카카오만 로그인 수단으로 사용하는 회원: 회원을 `WITHDRAWN`으로 전환하고 활성 GACHI refresh token을 모두 철회
- 서비스의 연결 해제 요청은 outbox에 먼저 기록하고 비동기로 카카오 API를 호출한다. 실패 시 지수 백오프로 재시도하며, 카카오 해제가 확인된 뒤 로컬 연동과 토큰을 한 트랜잭션에서 정리한다.
- 웹훅은 대표 Admin Key와 앱 ID를 모두 검증하며, 재전송되어도 동일하게 처리한다.
