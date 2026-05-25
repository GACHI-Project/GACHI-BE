# 가정통신문 AI 전체 분석 응답 저장 연동

이 문서는 BE가 AI 서버의 가정통신문 전체 분석 응답을 저장 모델에 반영하는 구현 결정을 정리한다. 상세 API 명세는 노션을 기준으로 관리한다.

## 결정 사항

- BE는 가정통신문 파이프라인의 AI 분석 단계에서 `POST /ai/newsletters/analyze`를 호출한다.
- AI 서버 응답의 top-level `title`, `summary`를 `newsletter.title`, `newsletter.summary`에 저장한다.
- AI 서버 응답의 `items`는 기존 checklist 저장 흐름에 연결한다.
- `dateCandidates` 요청 형식과 `items` 응답 형식은 기존 `extract-items` 계약을 유지한다.
- `meta`는 운영 보조 정보로 받고, 현재 BE 저장 모델에는 직접 저장하지 않는다.
- AI 서버 호출 실패 정책은 `docs/newsletter-ai-failure-policy.md`를 따른다.

## 저장 정책

- `title`: AI 응답 제목을 우선 저장한다. 빈 값이면 기존 BE fallback 제목을 사용한다.
- `summary`: AI 응답 요약을 우선 저장한다. 빈 값이면 기존 BE fallback 요약을 사용한다.
- `items`: `title`이 비어 있지 않은 항목만 checklist로 저장한다.
- `datetime`: TODO 저장 시 `targetDate`, `targetDateLabel` 생성에 사용한다. 파싱 실패 시 날짜 없이 저장한다.
- `dateStatus`: checklist 저장 모델에는 별도 컬럼이 없으므로 직접 저장하지 않는다. 단, `confirmed`이고 `datetime`이 파싱 가능한 항목은 calendar preview 생성 대상으로 사용한다.
- `calendar preview`: AI 분석으로 저장된 checklist/TODO ID와 확정 날짜를 묶어 Redis preview에 저장한다. `ambiguous`, `missing`, 날짜 파싱 실패 항목은 사용자가 확인해야 하므로 preview에 자동 포함하지 않는다.

## 호환 정책

- AI 서버의 `extract-items` baseline API는 유지하지만, BE 파이프라인은 `analyze`를 우선 사용한다.
- AI 서버가 `items`를 빈 배열로 반환해도 `title`, `summary`는 저장할 수 있다.
- 확정 날짜가 없는 분석 결과는 기존 Redis preview를 비워, 재분석 후 오래된 일정 후보가 남지 않게 한다.
- AI 서버 장애 시에는 OCR/번역/dateCandidates 스냅샷을 보존하고 newsletter 상태를 `FAILED`로 둔다.
