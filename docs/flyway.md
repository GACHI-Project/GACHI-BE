# Flyway Migration Guide

## 1. 경로 고정
- 마이그레이션 경로: `src/main/resources/db/migration`
- 설정값: `spring.flyway.locations=classpath:db/migration`

## 2. 파일 네이밍 규칙
- 형식: `V{버전}__{설명}.sql`
- 예시: `V1__init.sql`, `V2__add_user_table.sql`

## 3. 팀 규칙
- 이미 배포된 migration 파일은 수정하지 않음
- 변경이 필요하면 새 버전 파일을 추가함
- 버전 번호는 중복 금지, 누락 금지
- 한 migration 파일은 하나의 논리적 변경만 포함 권장

## 4. 환경별 적용 순서
1. dev: 로컬 DB에서 migration 실행 및 검증
2. stage: 동일 migration을 스테이징 DB에 적용
3. prod: 배포 직전 백업 후 동일 migration 적용

## 5. 운영 체크리스트
- 배포 전: 신규 migration 파일 검토 완료
- 배포 중: 앱 시작 로그에서 Flyway 성공 여부 확인
- 배포 후: `flyway_schema_history` 테이블 버전 확인

## 6. 실패 시 원칙
- 기존 migration 수정 금지
- 새로운 보정 migration(`V{next}__fix_*.sql`)으로 복구