# Local PostgreSQL Test Guide

## 목적
- 로컬에서 `application-test.yml` 테스트를 실행할 때, 기존 `gachi-postgres`와 인증정보 충돌로 실패하는 문제를 피한다.

## 권장 방법 (충돌 없는 전용 컨테이너)
1. 아래 스크립트를 실행한다.
```powershell
.\scripts\run-tests-with-postgres.ps1
```
2. 스크립트가 자동으로 수행한다.
- `postgres:16-alpine` 컨테이너를 `55432` 포트로 실행
- 준비 완료(`pg_isready`)까지 대기
- 테스트 실행 (`.\gradlew.bat --no-daemon test`)
- 테스트 종료 후 컨테이너 정리

## 기존 컨테이너를 쓰고 싶은 경우
- 이미 `5432`에서 실행 중인 PostgreSQL을 그대로 쓰려면 테스트 실행 전에 환경변수를 명시한다.
```powershell
$env:DB_URL='jdbc:postgresql://localhost:5432/<db_name>'
$env:DB_USERNAME='<db_user>'
$env:DB_PASSWORD='<db_password>'
.\gradlew.bat --no-daemon test
```
- 이 방식은 실행 중인 로컬 DB 계정/비밀번호와 정확히 일치해야 한다.
