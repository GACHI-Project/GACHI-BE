# BE Environment Variables

## Required
- `SPRING_PROFILES_ACTIVE`: `dev` / `stage` / `prod`
- `DB_URL`: PostgreSQL JDBC URL
- `DB_USERNAME`: DB user
- `DB_PASSWORD`: DB password

## Optional
- `AI_BASE_URL`: AI service base URL
- `AWS_REGION`: S3 region (e.g. `ap-northeast-2`)
- `AWS_S3_BUCKET`: S3 bucket name for uploads
- `AWS_S3_PUBLIC_BASE_URL`: optional CDN/custom domain base URL
- `AWS_S3_IMAGE_PREFIX`: object key prefix (default: `images`)

## AWS Credential
- Local/dev: use AWS CLI profile or environment credentials.
- EC2: recommended to attach an IAM Role to the instance and avoid static keys.
