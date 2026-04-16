# GACHI-BE

GACHI 프로젝트 백엔드 서버(Spring Boot) 레포지토리입니다.

## 문서
- `docs/api-response.md`: 공통 응답 포맷
- `docs/error-code.md`: 에러 코드 표
- `docs/flyway.md`: DB 마이그레이션 규칙
- `docs/env.md`: 환경 변수 가이드
- `docs/deploy.md`: 배포 가이드

## 협업 규칙
- 기본 브랜치: `develop`
- 브랜치 전략: `feat/xx`, `refac/xx`, `hotfix/xx`, `chore/xx`, `design/xx`, `bugfix/xx`
- 커밋 타입: `feat`, `fix`, `refactor`, `docs`, `style`, `chore`
- `main`, `develop` 직접 push 금지, PR 승인 후 머지
- CI 체크(`test-and-push`) 통과 후 머지

## 배포 태그 규칙
- `develop` push: `<dockerhub-id>/gachi-be:develop`
- `main` push: `<dockerhub-id>/gachi-be:latest`
