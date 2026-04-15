# BE 배포 가이드 (Docker)

## 1. 이미지 빌드

```bash
docker build -t gachi-be:latest .
```

## 2. SMTP 비밀번호 주입 방식 선택

- 로컬 개발: `.env`의 `SPRING_MAIL_PASSWORD` fallback 사용 가능
- 운영/공용 환경(stage/prod): `SPRING_MAIL_PASSWORD_SECRET_FILE` 기반 Docker Secret 사용 권장

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

## 3. Compose로 기동

```bash
docker compose --env-file .env up -d --remove-orphans
```

## 4. 헬스 체크

- `GET /actuator/health`

## 5. 권한 점검

- 배포 사용자(`ubuntu` 등)가 `SPRING_MAIL_PASSWORD_SECRET_FILE` 경로를 읽을 수 있어야 함
- 권한 점검 예시:

```bash
ls -l ./secrets/spring_mail_password.txt
test -r ./secrets/spring_mail_password.txt && echo "readable"
```
