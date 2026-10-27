# Google 간편 로그인

## 개요

프론트엔드는 Google Sign-In으로 발급받은 ID Token을 백엔드에 전달합니다. 백엔드는 Google 공개 키를 이용해 서명과 issuer, audience, 만료 시간을 검증한 뒤 Google 사용자 식별자인 `sub`를 연동 키로 사용합니다.

Google ID Token 원문은 데이터베이스나 Redis에 저장하지 않으며 로그에도 남기지 않습니다.

## 환경 변수

```env
GOOGLE_AUTH_ENABLED=true
GOOGLE_OAUTH_CLIENT_IDS=web-client-id.apps.googleusercontent.com,android-client-id.apps.googleusercontent.com
GOOGLE_SIGNUP_TOKEN_TTL_SECONDS=1800
```

`GOOGLE_OAUTH_CLIENT_IDS`에는 백엔드가 허용할 웹, Android, iOS OAuth 클라이언트 ID를 쉼표로 구분해 등록합니다. 운영 환경에서는 실제로 사용하는 클라이언트 ID만 허용해야 합니다.

## API 흐름

### 1. Google 인증 완료

`POST /api/v1/auth/google/complete`

```json
{
  "idToken": "google-id-token",
  "rememberMe": false
}
```

응답의 `status`에 따라 다음 동작을 선택합니다.

- `LOGIN_SUCCESS`: 이미 연동된 회원이며 GACHI access token과 refresh token이 반환됩니다.
- `SIGNUP_REQUIRED`: 신규 이메일입니다. 반환된 `signupToken`으로 GACHI 이용약관 동의 화면을 거쳐 회원가입을 완료합니다.
- `LINK_REQUIRED`: 같은 이메일의 GACHI 회원이 이미 있습니다. 자동 병합하지 않고 기존 계정으로 로그인한 뒤 마이페이지에서 명시적으로 연동합니다.

### 2. 신규 회원가입

`POST /api/v1/auth/google/signup`

```json
{
  "signupToken": "one-time-token",
  "consentAgreed": true,
  "languageCode": "KO",
  "notificationPreference": "IMPORTANT",
  "rememberMe": false
}
```

Google 간편 회원가입에서는 아이디, 비밀번호, 전화번호를 별도로 받지 않습니다. `signupToken`은 기본 30분 동안 유효하며 한 번만 사용할 수 있습니다.

### 3. 기존 계정 연동

`POST /api/v1/auth/google/link`

헤더에 기존 GACHI 계정의 `Authorization: Bearer {accessToken}`을 전달하고, 본문에 `/complete`에서 받은 `linkToken`을 전달합니다.

```json
{
  "linkToken": "one-time-token",
  "rememberMe": false
}
```

현재 로그인한 GACHI 계정과 검증된 Google 이메일이 같을 때만 연동됩니다.

## 보안 기준

- Google ID Token의 서명, issuer, audience, 만료 시간을 Google 라이브러리로 검증합니다.
- 이메일은 `email_verified=true`인 경우에만 사용합니다.
- 계정 식별에는 변경될 수 있는 이메일이 아니라 Google `sub`를 사용합니다.
- 가입 및 연동 토큰은 Redis에서 소비 즉시 삭제됩니다.
- 탈퇴 또는 비활성 회원에게는 로그인 토큰을 발급하지 않습니다.
