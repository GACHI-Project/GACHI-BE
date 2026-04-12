# Auth Rate Limit Policy

## 목적

인증 엔드포인트 남용(무차별 요청, 스팸성 호출)을 줄이기 위해 Redis 기반 Fixed Window Rate Limit를 적용한다.

## 대상 엔드포인트

- `POST /api/v1/auth/email/send`
- `POST /api/v1/auth/login`

## 클라이언트 IP 추출 규칙

1. `X-Forwarded-For` 헤더가 있으면 첫 번째 IP 사용
2. 없으면 `X-Real-IP` 사용
3. 둘 다 없으면 `HttpServletRequest#getRemoteAddr()` 사용

## 식별자 정책

- 이메일 발송 제한(`email/send`): `clientIp + HMAC_SHA256(normalizedEmail)`
- 로그인 제한(`login`): `clientIp`
- `normalizedEmail`: `trim + lowercase(Locale.ROOT)`

## Redis 키 전략

공통 prefix: `app.auth.rate-limit.key-prefix` (기본값 `auth:rate-limit:`)

- 이메일 발송:
  - 패턴: `{prefix}email-send:{clientIp}:{emailHash}`
- 로그인:
  - 패턴: `{prefix}login:{clientIp}`

## 임계값/윈도우(TTL)

설정 경로: `app.auth.rate-limit`

- `email-send.limit` (기본값: `3`)
- `email-send.window-seconds` (기본값: `300`)
- `login.limit` (기본값: `5`)
- `login.window-seconds` (기본값: `60`)

## 구현 방식

- Fixed Window 카운터
- Redis Lua 스크립트로 `INCR + EXPIRE + TTL`을 원자적으로 수행
- 경쟁 상황에서도 카운트/TTL 일관성을 보장

## 초과 응답 정책

- HTTP Status: `429 TOO_MANY_REQUESTS`
- 에러 코드:
  - `AUTH4293`: 로그인 IP rate limit 초과
  - `AUTH4294`: 이메일 발송(IP+email 해시) rate limit 초과

## 보안 정책

- `app.auth.rate-limit.enabled=true`일 때 `app.auth.rate-limit.email-hmac-secret` 값은 필수다.
- 시크릿은 `application.yml` 하드코딩이 아닌 환경변수(`AUTH_RATE_LIMIT_EMAIL_HMAC_SECRET`) 또는 Secret Manager로 주입한다.
