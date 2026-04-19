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
SWAGGER_ENABLED=false
SWAGGER_BASIC_AUTH_FILE=./secrets/swagger_htpasswd.txt
SWAGGER_TLS_CERT_FILE=./secrets/swagger_tls.crt
SWAGGER_TLS_KEY_FILE=./secrets/swagger_tls.key
SWAGGER_TLS_MODE=letsencrypt_ip
SWAGGER_TLS_IP=<your-elastic-ip>
# letsencrypt_ip 모드에서는 사용되지 않음 (self-signed 폴백 시 CN 용도)
SWAGGER_TLS_COMMON_NAME=
CERTBOT_EMAIL=devops@example.com
```

### 2-3. Swagger Basic Auth 파일 준비 예시

```bash
cd /home/ubuntu/GACHI-BE/deploy
mkdir -p secrets
printf "swagger:$(openssl passwd -apr1 'change-me')\n" > secrets/swagger_htpasswd.txt
chmod 600 secrets/swagger_htpasswd.txt
```

### 2-4. Swagger HTTPS 인증서 전략

- `SWAGGER_TLS_MODE=letsencrypt_ip`일 때:
  - 배포 워크플로우가 Let’s Encrypt IP 인증서를 발급/갱신하고,
  - 발급된 인증서를 `./secrets/swagger_tls.crt`, `./secrets/swagger_tls.key`로 동기화함
  - certbot 컨테이너 이미지는 `certbot/certbot:5.4.0`으로 고정되어 있음
- `SWAGGER_TLS_MODE`가 다른 값이면:
  - `SWAGGER_TLS_CERT_FILE`, `SWAGGER_TLS_KEY_FILE` 파일을 직접 준비해야 함
  - 파일이 없으면 self-signed 인증서를 생성함(운영 비권장)
- IP 인증서 운영에서는 `SWAGGER_TLS_IP`를 탄력 IP로 고정하고, `CERTBOT_EMAIL`을 실제 운영 메일로 설정하는 것을 권장함

### 2-5. Swagger 열기/닫기 운영 절차

```bash
# Swagger 열기
sed -i 's/^SWAGGER_ENABLED=.*/SWAGGER_ENABLED=true/' .env

# Swagger 닫기
sed -i 's/^SWAGGER_ENABLED=.*/SWAGGER_ENABLED=false/' .env
```

- 값 변경 후에는 `deploy-ec2` 워크플로우를 다시 실행해 컨테이너 설정을 반영해야 함

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

### 6-1. 배포 번들 동기화 대상(화이트리스트)

- `deploy/docker-compose.yml`
- `deploy/.env.example`
- `deploy/HTTPS-SETUP.md`
- `deploy/nginx/**`
- `deploy/secrets/spring_mail_password.example.txt`
- `deploy/secrets/swagger_htpasswd.example.txt`

### 6-2. 제외 정책

- 서버 운영값이 들어있는 `deploy/.env`는 전송하지 않음
- 운영 시크릿 파일(예: `deploy/secrets/spring_mail_password.txt`)은 전송하지 않음
- 운영 Swagger 인증 파일(예: `deploy/secrets/swagger_htpasswd.txt`)은 전송하지 않음
- 운영 TLS 인증서/키 파일(예: `deploy/secrets/swagger_tls.crt`, `deploy/secrets/swagger_tls.key`)은 전송하지 않음
- 즉, EC2에 이미 존재하는 `.env`/실시크릿 파일을 보존한 상태로 compose/nginx 설정만 동기화함

### 6-3. 워크플로우 실행 순서(Actions 기준)

1. GitHub Actions에서 AWS 자격증명 설정(OIDC 우선, Access Key fallback)
2. `deploy/` 화이트리스트 파일 + `scripts/deploy-ec2.sh`를 번들로 묶어 SSM `AWS-RunShellScript`로 EC2에 전달
3. EC2에서 번들을 배포 경로에 해제(기존 `.env`/실시크릿 파일은 유지)
4. (`[1/7]`) EC2 배포 경로로 이동
5. (`[2/7]`) EC2에서 `.env` 존재 여부 확인(없으면 즉시 실패)
6. (`[3/7]`) `.env` 내 `BACKEND_IMAGE`를 최신 태그로 갱신
7. (`[4/7]`) SMTP 시크릿 파일 검증 + `SWAGGER_ENABLED=true`인 경우 Swagger Basic Auth 파일/HTTPS 인증서 파일 검증(없으면 self-signed 생성)
   - `SWAGGER_TLS_MODE=letsencrypt_ip`이면 Let’s Encrypt IP 인증서 발급/갱신 및 `./secrets/swagger_tls.*` 동기화 수행
8. (`[5/7]`) `docker compose pull backend`
9. (`[6/7]`) `docker compose up -d --remove-orphans backend` 실행 후 backend health 확인, 통과 시 `nginx`를 `--no-deps --force-recreate`로 재기동
10. (`[7/7]`) 미사용 이미지 정리(`docker image prune -f`) → `docker compose ps`로 최종 상태 출력 → `docker compose logs --tail=80 backend nginx`로 로그 요약 출력

### 6-4. 실패 시 로그 확인

- 초기 검증 실패는 해당 에러 메시지만 출력될 수 있으며, Docker 단계 이후 실패 시 관련 서비스 로그가 일부 출력됨
- 성공 시 `[7/7]`에서 `docker compose ps`와 `docker compose logs --tail=80 backend nginx`를 출력함
- Actions 로그에서 SSM stdout/stderr를 함께 출력해 실패 지점 추적이 가능함
- 단, SSM `get-command-invocation`의 stdout/stderr는 최대 24,000자까지만 반환되므로, 전체 로그가 필요하면 EC2에서 직접 `docker compose logs`를 확인하거나 Run Command의 S3/CloudWatch 출력 연동을 사용해야 함

### 6-5. 필수 시크릿/권한

- GitHub Secrets 권장 구성:
  - `EC2_INSTANCE_ID`(필수)
  - `DOCKERHUB_USERNAME`(필수)
  - `EC2_DEPLOY_PATH`(선택, 기본 `/home/ubuntu/GACHI-BE/deploy`)
  - `EC2_HOST`(조건부 필수: `SWAGGER_ENABLED=true` + `SWAGGER_TLS_MODE=letsencrypt_ip` + `.env`에 `SWAGGER_TLS_IP` 미설정 시)
  - `AWS_REGION`(선택, 기본 `ap-northeast-2`)
  - `AWS_OIDC_ROLE_ARN`(권장) 또는 `AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY`
- SSH 비밀키(`EC2_SSH_KEY`)와 SSH 사용자(`EC2_USER`) 없이 deploy 워크플로우가 동작해야 함
- backend 이미지가 private이면 EC2 Docker 인증이 사전 구성되어 있어야 함
  - 권장: 인스턴스에서 `docker login`을 미리 수행
  - 대안: EC2 인스턴스 역할로 SSM Parameter Store SecureString/Secrets Manager에서 토큰을 조회해 `scripts/deploy-ec2.sh` 실행 환경의 `DOCKERHUB_TOKEN`으로 주입
  - GitHub Secret `DOCKERHUB_TOKEN`은 deploy-ec2 워크플로우가 SSM 명령 본문으로 전달하지 않음
- `DOCKERHUB_USERNAME`은 SSM 명령 본문으로 전달되므로 SSM/CloudTrail 이력에 평문으로 남음(토큰이 아닌 계정 식별자)
- 워크플로우 권한 `id-token: write`는 OIDC 우선 경로를 위한 설정이며, Access Key fallback 사용 시에는 토큰이 발급되더라도 사용되지 않음
- OIDC Role(IAM) 최소 권한:
  - `ssm:SendCommand`
    - 리소스: `arn:aws:ec2:<region>:<account-id>:instance/<instance-id>`
    - 리소스: `arn:aws:ssm:<region>::document/AWS-RunShellScript`
  - `ssm:GetCommandInvocation`
- 운영 점검/확장 시 선택 권한:
  - `ssm:ListCommandInvocations`
  - `ec2:DescribeInstances`
- SSM 실행/폴링 시간 정렬 확인:
  - `SSM executionTimeout`: `SSM_EXECUTION_TIMEOUT_SECONDS` (현재 `3600`)
  - `Actions polling`: `SSM_POLL_INTERVAL_SECONDS` 간격으로 최대 `executionTimeout`까지 조회(현재 `10초 * 360회 ≈ 60분`)
- 대상 EC2 인스턴스에는 SSM Agent와 `AmazonSSMManagedInstanceCore` 권한이 필요함

## 7. Swagger IP 인증서 자동 갱신

- 워크플로우 파일: `.github/workflows/renew-swagger-ip-tls.yml`
- 트리거:
  - 스케줄 실행(12시간 주기)
  - 수동 실행(`workflow_dispatch`)
- 목적: 배포가 없어도 6일 만료 IP 인증서를 주기적으로 갱신하고 nginx에 반영

### 7-1. 실행 순서

1. GitHub Actions에서 AWS 자격증명 설정(OIDC 우선, Access Key fallback)
2. `scripts/renew-swagger-ip-tls.sh`를 SSM `AWS-RunShellScript`로 EC2에 전달/실행
3. EC2 배포 경로/`.env` 존재 확인
4. `SWAGGER_ENABLED=true` + `SWAGGER_TLS_MODE=letsencrypt_ip`일 때만 실행
5. `SWAGGER_TLS_IP`(없으면 `EC2_HOST`) 기준으로 `certbot certonly --keep-until-expiring` 수행
6. `./secrets/swagger_tls.crt`, `./secrets/swagger_tls.key`로 인증서 동기화
7. `nginx -s reload`로 무중단 반영(실패 시 재기동 fallback)
8. `openssl x509 -noout -dates`로 만료일 출력

### 7-2. 점검 포인트

- EC2 보안그룹에서 80/443 포트가 열려 있어야 함
- `CERTBOT_EMAIL`을 실제 운영 메일로 입력해야 인증서 만료/이슈 알림 수신이 가능함
- SSH 비밀키(`EC2_SSH_KEY`) 없이도 renew 워크플로우가 동작해야 함
- GitHub Secrets 권장 구성:
  - `EC2_INSTANCE_ID`(필수)
  - `AWS_REGION`(선택, 기본 `ap-northeast-2`)
  - `AWS_OIDC_ROLE_ARN`(권장) 또는 `AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY`
  - `EC2_HOST`(선택, `.env`에 `SWAGGER_TLS_IP`가 비어 있을 때 fallback)
- OIDC Role(IAM) 최소 권한:
  - `ssm:SendCommand`
    - 리소스: `arn:aws:ec2:<region>:<account-id>:instance/<instance-id>`
    - 리소스: `arn:aws:ssm:<region>::document/AWS-RunShellScript`
  - `ssm:GetCommandInvocation`
- 운영 점검/확장 시 선택 권한:
  - `ssm:ListCommandInvocations`
  - `ec2:DescribeInstances`
- SSM 실행/폴링 시간 정렬 확인:
  - `SSM executionTimeout`: `SSM_EXECUTION_TIMEOUT_SECONDS` (현재 `3600`)
  - `Actions polling`: `SSM_POLL_INTERVAL_SECONDS` 간격으로 최대 `executionTimeout`까지 조회(현재 `10초 * 360회 ≈ 60분`)
- 대상 EC2 인스턴스에는 SSM Agent와 `AmazonSSMManagedInstanceCore` 권한이 필요함
- 수동 점검 명령:

```bash
docker compose --env-file .env logs --tail=120 nginx
openssl x509 -in ./secrets/swagger_tls.crt -noout -issuer -dates
```
