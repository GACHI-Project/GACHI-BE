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
- `AUTH_EMAIL_SUBJECT`: subject line for verification email

## AWS Credential
- Local/dev: use AWS CLI profile or environment credentials.
- EC2: recommended to attach an IAM Role to the instance and avoid static keys.
