## 📌 작업 요약
- 요약:
  - develop 브랜치의 누적 변경사항을 main으로 릴리즈 배포
- 관련 이슈: closes #

## 🌿 브랜치 정보
- **Source**: `develop`
- **Target**: `main` (릴리즈, 핫픽스)

## 🧩 변경 타입
- [ ] feat: 새로운 기능 추가
- [ ] fix: 버그 수정
- [ ] refactor: 코드 리팩토링
- [ ] docs: 문서 수정
- [ ] style: 코드 포맷팅, 세미콜론 누락 등
- [ ] chore: 빌드 업무, 패키지 매니저 설정 등

## ✅ 체크리스트
- [x] 브랜치 컨벤션 준수 (`feat/refac/hotfix/chore/design/bugfix`)
- [x] 커밋 컨벤션 준수 (`feat/fix/refactor/docs/style/chore`)
- [x] self-review 완료
- [x] 테스트 및 로컬 실행 확인 완료

## 🧪 테스트 결과
- GitHub Actions `deploy-ec2` 실행 확인 (`workflow_dispatch`, ref: `main`)
  - 결과:
  - 스크린샷: ![scp-step]()

- 원격 배포 순서/재기동 확인
  - 결과:
  - 스크린샷: ![ssh-order]()

- 배포 후 컨테이너 상태 확인
  - 결과:
  - 스크린샷: ![compose-ps]()
