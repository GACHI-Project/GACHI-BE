# BE Environment Variables

## Required
- `SPRING_PROFILES_ACTIVE`: `dev` / `stage` / `prod`
- `DB_URL`: PostgreSQL JDBC URL
- `DB_USERNAME`: DB user
- `DB_PASSWORD`: DB password
- `JWT_SECRET`: JWT signing secret (at least 32 bytes)

## Optional

- `AI_BASE_URL`: AI service base URL
- `AWS_REGION`: S3 region (e.g. `ap-northeast-2`)
- `AWS_S3_BUCKET`: S3 bucket name for uploads
- `AWS_S3_PUBLIC_BASE_URL`: optional CDN/custom domain base URL
- `AWS_S3_IMAGE_PREFIX`: object key prefix (default: `images`)
- `REDIS_HOST`: Redis host (default: `localhost`)
- `REDIS_PORT`: Redis port (default: `6379`)
- `REDIS_PASSWORD`: Redis password
- `REDIS_TIMEOUT_MS`: Redis timeout duration (default: `2000ms`)
- `JWT_ISSUER`: token issuer (default: `gachi-be`)
- `JWT_ACCESS_TOKEN_MINUTES`: access token TTL minutes (default: `15`)
- `JWT_REFRESH_TOKEN_DAYS`: refresh token TTL days when remember-me is off (default: `7`)
- `JWT_REFRESH_TOKEN_REMEMBER_DAYS`: refresh token TTL days when remember-me is on (default: `30`)
- `AUTH_CONSENT_VERSION`: signup consent version tag
- `AUTH_EMAIL_STORE`: `redis` or `memory` (default: `redis`)
- `AUTH_EMAIL_CODE_TTL_SECONDS`: email code TTL seconds (default: `300`)
- `AUTH_EMAIL_RESEND_COOLDOWN_SECONDS`: resend cooldown seconds (default: `60`)
- `AUTH_EMAIL_MAX_ATTEMPTS`: max verification attempts per code (default: `5`)
- `AUTH_EMAIL_VERIFIED_TTL_SECONDS`: verified mark TTL before signup (default: `1800`)
- `SPRING_MAIL_HOST`: SMTP 서버 주소 (SMTP 발송 사용 시 필수)
- `SPRING_MAIL_PORT`: SMTP 포트 (일반적으로 `587`)
- `SPRING_MAIL_USERNAME`: SMTP 로그인 계정
- `SPRING_MAIL_PASSWORD`: SMTP 비밀번호 또는 앱 비밀번호 (로컬 개발 fallback 용도)
- `SPRING_MAIL_PASSWORD_SECRET_FILE`: SMTP 비밀번호 Docker Secret 파일 경로 (기본값: `./secrets/spring_mail_password.example.txt`)
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`: SMTP 인증 사용 여부
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`: STARTTLS 사용 여부
- `AUTH_EMAIL_FROM_ADDRESS`: 인증 메일 발신 주소 (SMTP 사용 시 필수)
- `AUTH_EMAIL_REPLY_TO`: 인증 메일 회신 기본 주소 (선택)
- `AUTH_EMAIL_NOOP_ALLOWED`: SMTP 미구성 시 로그 전용 발송기 허용 여부 (기본값: `false`)
- `AUTH_EMAIL_SUBJECT`: 인증 메일 제목
- 운영 권장: `SPRING_MAIL_PASSWORD`를 `.env`에 직접 넣기보다 `SPRING_MAIL_PASSWORD_SECRET_FILE`로 파일 주입 사용
- 운영 권장: Secret 파일 권한은 최소 권한으로 관리하고, 배포 사용자(`ubuntu` 등)가 읽을 수 있어야 함 (`chmod 600 <secret-file>`)
- `AUTH_RATE_LIMIT_ENABLED`: enable auth endpoint rate limiting (default: `true`)
- `AUTH_RATE_LIMIT_KEY_PREFIX`: Redis key prefix for auth rate-limit counters (default: `auth:rate-limit:`)
- `AUTH_RATE_LIMIT_EMAIL_HMAC_SECRET`: HMAC secret for email identifier hashing (required when rate limit is enabled)
- `AUTH_RATE_LIMIT_TRUSTED_PROXIES`: trusted proxy list for forwarded headers (IP/CIDR only, comma-separated; default: `127.0.0.1,::1`)
- `AUTH_RATE_LIMIT_EMAIL_SEND_LIMIT`: max allowed requests per window for `/api/v1/auth/email/send` (default: `3`)
- `AUTH_RATE_LIMIT_EMAIL_SEND_WINDOW_SECONDS`: window size seconds for `/api/v1/auth/email/send` (default: `300`)
- `AUTH_RATE_LIMIT_LOGIN_LIMIT`: max allowed requests per window for `/api/v1/auth/login` (default: `5`)
- `AUTH_RATE_LIMIT_LOGIN_WINDOW_SECONDS`: window size seconds for `/api/v1/auth/login` (default: `60`)

## AWS Credential
- Local/dev: use AWS CLI profile or environment credentials.
- EC2: recommended to attach an IAM Role to the instance and avoid static keys.
