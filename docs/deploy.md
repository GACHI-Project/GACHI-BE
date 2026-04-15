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

## 6. GitHub Actions 자동 배포(main 브랜치)

- 워크플로우 파일: `.github/workflows/deploy-ec2.yml`
- 트리거: `main` 브랜치 push 또는 수동 실행(`workflow_dispatch`)
- 목적: 서버에서 `git pull` 없이 `deploy/` 설정 파일을 EC2에 동기화하고 컨테이너를 재기동

### 6-1. SCP 동기화 대상(화이트리스트)

- `deploy/docker-compose.yml`
- `deploy/.env.example`
- `deploy/HTTPS-SETUP.md`
- `deploy/nginx/**`
- `deploy/secrets/spring_mail_password.example.txt`

### 6-2. 제외 정책

- 서버 운영값이 들어있는 `deploy/.env`는 전송하지 않음
- 운영 시크릿 파일(예: `deploy/secrets/spring_mail_password.txt`)은 전송하지 않음
- 즉, EC2에 이미 존재하는 `.env`/실시크릿 파일을 보존한 상태로 compose/nginx 설정만 동기화함

### 6-3. 원격 배포 실행 순서

1. `deploy/` 화이트리스트 파일을 SCP로 EC2 배포 경로에 복사
2. EC2에서 `.env` 존재 여부 확인(없으면 즉시 실패)
3. `.env` 내 `BACKEND_IMAGE`를 최신 태그로 갱신
4. SMTP 시크릿 파일 경로 및 권한 검증
5. `docker compose pull backend`
6. `docker compose up -d --remove-orphans backend`
7. `docker compose up -d --remove-orphans --force-recreate nginx`
8. `docker compose ps`로 최종 상태 출력

### 6-4. 실패 시 로그 확인

- 배포 스크립트는 실패 시 `docker compose ps`와 `docker compose logs --tail=80 backend nginx`를 출력함
- Actions 로그만으로 실패 지점과 컨테이너 상태를 바로 확인할 수 있음
