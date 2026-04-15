# BE Deployment (Docker)

## 1. Build image

```bash
docker build -t gachi-be:latest .
```

## 2. Choose SMTP password injection strategy

- Local development: allow `.env` fallback with `SPRING_MAIL_PASSWORD`
- Shared/staging/prod: prefer Docker Secret via `SPRING_MAIL_PASSWORD_SECRET_FILE`

### 2-1. Secret file setup example

```bash
cd /home/ubuntu/GACHI-BE/deploy
mkdir -p secrets
printf '%s' 'your-real-smtp-password' > secrets/spring_mail_password.txt
chmod 600 secrets/spring_mail_password.txt
```

### 2-2. `.env` example

```bash
SPRING_MAIL_PASSWORD=
SPRING_MAIL_PASSWORD_SECRET_FILE=./secrets/spring_mail_password.txt
```

## 3. Run with compose

```bash
docker compose --env-file .env up -d --remove-orphans
```

## 4. Health check

- `GET /actuator/health`

## 5. Permission check

- Ensure the deployment user (`ubuntu`, etc.) can read `SPRING_MAIL_PASSWORD_SECRET_FILE`
- Permission check example:

```bash
ls -l ./secrets/spring_mail_password.txt
test -r ./secrets/spring_mail_password.txt && echo "readable"
```
