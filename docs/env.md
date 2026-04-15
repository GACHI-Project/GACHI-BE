# BE Environment Variables

## 필수 (Required)

- `SPRING_PROFILES_ACTIVE`: 실행 프로필 (`dev` / `stage` / `prod`)
- `DB_URL`: PostgreSQL JDBC 접속 URL
- `DB_USERNAME`: DB 접속 사용자명
- `DB_PASSWORD`: DB 접속 비밀번호
- `JWT_SECRET`: JWT 서명 시크릿 (최소 32바이트 이상 권장)

## 선택 (Optional)

- `AI_BASE_URL`: BE가 내부적으로 호출하는 AI 서비스 기본 URL
- `AWS_REGION`: S3 리전 (예: `ap-northeast-2`)
- `AWS_S3_BUCKET`: 업로드 파일 저장용 S3 버킷 이름
- `AWS_S3_PUBLIC_BASE_URL`: CDN/커스텀 도메인 등 공개 접근용 베이스 URL
- `AWS_S3_IMAGE_PREFIX`: S3 객체 키 prefix (기본값: `images`)
- `REDIS_HOST`: Redis 호스트 (기본값: `localhost`)
- `REDIS_PORT`: Redis 포트 (기본값: `6379`)
- `REDIS_PASSWORD`: Redis 비밀번호
- `REDIS_TIMEOUT_MS`: Redis 타임아웃 (기본값: `2000ms`)
- `JWT_ISSUER`: 토큰 발급자 식별자 (기본값: `gachi-be`)
- `JWT_ACCESS_TOKEN_MINUTES`: Access Token 만료 시간(분) (기본값: `15`)
- `JWT_REFRESH_TOKEN_DAYS`: 일반 Refresh Token 만료 기간(일) (기본값: `7`)
- `JWT_REFRESH_TOKEN_REMEMBER_DAYS`: remember-me 사용 시 Refresh Token 만료 기간(일) (기본값: `30`)
- `AUTH_CONSENT_VERSION`: 회원가입 동의 버전 태그
- `AUTH_EMAIL_STORE`: 이메일 인증 저장소 타입 (`redis` 또는 `memory`, 기본값: `redis`)
- `AUTH_EMAIL_CODE_TTL_SECONDS`: 인증코드 유효시간(초) (기본값: `300`)
- `AUTH_EMAIL_MAX_ATTEMPTS`: 인증코드 최대 검증 시도 횟수 (기본값: `5`)
- `AUTH_EMAIL_RESEND_COOLDOWN_SECONDS`: 인증코드 재전송 대기시간(초) (기본값: `60`)
- `AUTH_EMAIL_VERIFIED_TTL_SECONDS`: 인증 완료 상태 유지시간(초) (기본값: `1800`)

### SMTP / 메일 발송

- `SPRING_MAIL_HOST`: SMTP 서버 주소 (SMTP 발송 사용 시 필수)
- `SPRING_MAIL_PORT`: SMTP 포트 (일반적으로 `587`)
- `SPRING_MAIL_USERNAME`: SMTP 로그인 계정
- `SPRING_MAIL_PASSWORD`: SMTP 비밀번호 또는 앱 비밀번호 (로컬 개발 fallback 용도)
- `SPRING_MAIL_PASSWORD_SECRET_FILE`: SMTP 비밀번호 Docker Secret 파일 경로 (기본값: `./secrets/spring_mail_password.example.txt`)
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`: SMTP 인증 사용 여부
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`: STARTTLS 사용 여부
- `AUTH_EMAIL_FROM_ADDRESS`: 인증 메일 발신 주소 (SMTP 사용 시 필수)
- `AUTH_EMAIL_NOOP_ALLOWED`: SMTP 미구성 시 로그 전용 발송기 허용 여부 (기본값: `false`)
- `AUTH_EMAIL_REPLY_TO`: 인증 메일 회신 기본 주소 (선택)
- `AUTH_EMAIL_SUBJECT`: 인증 메일 제목
- 운영 권장: `SPRING_MAIL_PASSWORD`를 `.env`에 직접 넣기보다 `SPRING_MAIL_PASSWORD_SECRET_FILE` 기반 파일 주입 사용
- 운영 권장: Secret 파일은 최소 권한으로 관리하고, 배포 사용자(`ubuntu` 등)가 읽을 수 있어야 함 (`chmod 600 <secret-file>`)

### 인증 Rate Limit

- `AUTH_RATE_LIMIT_ENABLED`: 인증 API rate limit 활성화 여부 (기본값: `true`)
- `AUTH_RATE_LIMIT_KEY_PREFIX`: Redis rate limit 키 prefix (기본값: `auth:rate-limit:`)
- `AUTH_RATE_LIMIT_EMAIL_HMAC_SECRET`: 이메일 식별자 해싱용 HMAC 시크릿 (rate limit 활성화 시 필수)
- `AUTH_RATE_LIMIT_TRUSTED_PROXIES`: 전달 헤더를 신뢰할 프록시 목록 (IP/CIDR, 쉼표 구분, 기본값: `127.0.0.1,::1`)
- `AUTH_RATE_LIMIT_EMAIL_SEND_LIMIT`: `/api/v1/auth/email/send` 윈도우 내 허용 횟수 (기본값: `3`)
- `AUTH_RATE_LIMIT_EMAIL_SEND_WINDOW_SECONDS`: `/api/v1/auth/email/send` 윈도우 길이(초) (기본값: `300`)
- `AUTH_RATE_LIMIT_LOGIN_LIMIT`: `/api/v1/auth/login` 윈도우 내 허용 횟수 (기본값: `5`)
- `AUTH_RATE_LIMIT_LOGIN_WINDOW_SECONDS`: `/api/v1/auth/login` 윈도우 길이(초) (기본값: `60`)

## AWS Credential

- Local/dev: AWS CLI profile 또는 환경변수 자격증명 사용
- EC2: 정적 키 대신 IAM Role 부여 방식 권장
