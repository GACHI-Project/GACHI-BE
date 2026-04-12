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
- `AUTH_EMAIL_FROM_ADDRESS`: from address for verification emails (required when SMTP is enabled)
- `AUTH_EMAIL_NOOP_ALLOWED`: allow log-only mail sender when SMTP is missing (default: `false`)
- `AUTH_EMAIL_SUBJECT`: subject line for verification email
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
