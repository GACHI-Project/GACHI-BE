# Error Codes

상세 에러 코드는 아래 Google Sheets를 단일 원본으로 관리합니다.

- Google Sheets: [Error Code Single Source](https://docs.google.com/spreadsheets/d/1sUG_JJsVl6a8nR6k8jO_b7UrIEZzuvQdPB6S2S6fqgo/edit?gid=1446419637#gid=1446419637)

## 운영 원칙

1. 코드 추가/수정은 시트에서 먼저 반영합니다.
2. 코드 반영 시 `src/main/java/com/gachi/be/global/code/ErrorCode.java`와 동기화합니다.
3. PR에는 변경된 에러코드의 핵심 정보(코드/HTTP 상태코드/의도)를 남기고, 시트 참조는 `에러코드 스냅샷 탭(vN): error-code-vN(gid=<숫자>)` 형식의 링크로 기록합니다.
4. PR 시점의 에러코드 스냅샷은 버전 탭(`error-code-vN`)에 보관하며, PR 본문에 해당 탭 링크(gid 포함)와 커밋 해시를 기록합니다.
