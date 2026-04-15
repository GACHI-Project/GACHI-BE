# BE Deployment (Docker)

## 1. Build image
```bash
docker build -t gachi-be:latest .
```

## 2. SMTP 비밀번호 주입 방식 선택
- 로컬 개발: `.env`의 `SPRING_MAIL_PASSWORD` fallback 사용 가능
- 운영/공용 환경: `SPRING_MAIL_PASSWORD_SECRET_FILE` 기반 Docker Secret 사용 권장

### 2-1. Secret 파일 준비 예시
```bash
cd /home/ubuntu/GACHI-BE/deploy
mkdir -p secrets
printf '%s' 'your-real-smtp-password' > secrets/spring_mail_password.txt
chmod 600 secrets/spring_mail_password.txt
```

### 2-2. `.env` 설정 예시
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

## 5. 권한 점검
- 배포 사용자(`ubuntu` 등)가 `SPRING_MAIL_PASSWORD_SECRET_FILE` 경로 파일을 읽을 수 있어야 함
- 권한 확인 예시:
```bash
ls -l ./secrets/spring_mail_password.txt
test -r ./secrets/spring_mail_password.txt && echo "readable"
```
