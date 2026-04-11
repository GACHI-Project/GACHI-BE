# Error Codes

상세 에러 코드는 아래 Google Sheets를 단일 원본으로 관리합니다.

- Google Sheets: [Error Code Single Source](https://docs.google.com/spreadsheets/d/1sUG_JJsVl6a8nR6k8jO_b7UrIEZzuvQdPB6S2S6fqgo/edit?gid=657937#gid=657937)

## 운영 원칙

1. 코드 추가/수정은 시트에서 먼저 반영합니다.
2. 코드 반영 시 `src/main/java/com/gachi/be/global/code/ErrorCode.java`와 동기화합니다.
3. PR에는 변경된 코드와 설명(의도/상태코드/메시지), 그리고 반영한 시트 탭(vN/gid)을 함께 남깁니다.
4. PR 시점의 에러코드 스냅샷은 Error Code Single Source의 버전 탭(vN)에 보관하고, PR 본문에 해당 탭 링크(gid)와 커밋 해시를 기록합니다.
