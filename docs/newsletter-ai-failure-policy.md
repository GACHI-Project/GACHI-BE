# AI 서버 장애 시 가정통신문 처리 정책

이 문서는 API 명세서가 아니라 BE가 장애 상황에서 어떤 상태와 데이터를 남길지 정한 운영 정책이다.
상세 API 명세는 노션을 기준으로 관리한다.

## 결정 사항

- AI 서버 호출 실패 시 newsletter 상태는 `FAILED`로 둔다.
- 별도 부분 성공 상태는 이번 범위에서 추가하지 않는다.
- OCR, 정제 원문, 번역 결과, 날짜 후보는 가능한 범위까지 저장한다.
- AI 분석 결과에서 파생되는 제목, 요약, checklist, calendar event는 생성하지 않는다.
- 실패 원인 추적을 위해 `failureStage`, `failureReason`을 저장한다.
- 상태 조회 응답은 `FAILED`일 때 `canRetry=true`와 실패 단계를 반환한다.
- 사용자는 `POST /api/v1/newsletters/{newsletterId}/analysis/retry`로 같은 문서를 다시 분석할 수 있다.

## 실패 시 저장 범위

AI 서버 단계에서 실패하면 다음 데이터는 남긴다.

- `status = FAILED`
- `ocrText`
- `originalText`
- `translatedText`
- `dateCandidates`
- `failureStage = AI_SERVER`
- `failureReason`

다음 데이터는 비워 둔다.

- `title`
- `summary`
- checklist
- calendar event

## 재시도 정책

재시도는 `FAILED` 상태에서만 허용한다.

재시도 요청이 들어오면 기존 checklist와 calendar event 파생 데이터를 삭제하고, newsletter 상태를 `PENDING`으로 되돌린 뒤 파이프라인을 다시 실행한다.

자동 재시도 큐는 이번 범위에서 만들지 않는다. AI 서버 장애와 문서 입력 문제를 자동으로 구분하기 어렵고, 외부 API 비용과 장애 확산 위험이 있기 때문이다.

추후 필요하면 `failureStage=AI_SERVER`인 건만 백오프 큐에 넣는 방식으로 확장한다.
