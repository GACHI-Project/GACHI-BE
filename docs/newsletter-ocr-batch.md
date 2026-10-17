# 라벨링용 가정통신문 OCR 일괄 추출

`newsletterOcrBatch`는 Spring 애플리케이션을 실행하지 않는 개발자용 Gradle 작업이다.
DB, Redis, 로그인, 앱 업로드, Papago, OpenAI 설정은 필요하지 않다.
웹 API를 추가하지 않으며 운영 서버에 배포할 필요도 없다.

## 처리 방식

- PDF 한 파일은 여러 페이지를 포함한 문서 한 건으로 처리한다.
- JPG/JPEG/PNG는 파일 하나를 문서 한 건으로 처리한다.
- 여러 이미지를 한 문서로 묶거나 기존 라벨의 식별자를 유지하려면 manifest를 사용한다.
- HWP/HWPX는 지원하지 않는다. PDF로 내보낸 뒤 기존 sampleId를 유지하여 manifest에 등록한다.
  변환 전 파일명 등 출처 메타데이터는 라벨에 별도로 보관한다. 폴더에서 발견한 HWP/HWPX는
  `FAILED / INVALID_INPUT`으로 기록하며 OCR을 호출하지 않는다.
- 이미지에는 서비스의 EXIF 회전 보정 로직을 적용한다.
- 서비스의 `ClovaOcrClient.callOcrPages`, `OcrTextRefiner`,
  `NewsletterDateCandidateExtractor`를 직접 재사용한다.
- OCR 결과를 페이지별로 정제한 뒤, 서비스와 공유하는 결합 함수로 줄바꿈 한 개를 넣어 연결한다.
- 날짜 후보는 최종 `originalText`에서 추출한다. 연도가 없는 날짜는 지정한 기준일의 연도를 따른다.
  문서 발행일이나 정답 날짜에 맞추어 후보를 사람이 수정하지 않는다.

## 실행

저장소 루트에서 Java 17 toolchain으로 실행한다. 아래 예시는 PowerShell 기준이다.
`local-newsletter-ocr/`는 Git에서 제외되므로 이 안에 원본과 결과를 보관하는 것을 권장한다.

먼저 `local-newsletter-ocr/input/`에 문서를 준비하고 대상을 확인한다.

```powershell
.\gradlew.bat newsletterOcrBatch --args='--input local-newsletter-ocr/input --output local-newsletter-ocr/results --reference-date 2026-10-18'
```

기본값은 dry-run이다. 대상 문서 수와 식별자만 출력하며 환경 변수, 외부 호출, 결과 저장이 없다.
폴더는 하위 폴더까지 탐색하며 지원 파일과 HWP/HWPX만 대상으로 삼는다.
각 파일의 상대 경로로 안정적인 `doc_...` 식별자를 생성한다.
출력 폴더는 입력 폴더 밖에 지정한다.

실제 호출에는 아래 환경 변수가 필요하다. `.env`는 자동으로 읽지 않는다.

| 환경 변수 | 내용 |
| --- | --- |
| `AWS_S3_BUCKET` | 임시 OCR 파일을 업로드할 버킷 |
| `AWS_REGION` | 기본 `ap-northeast-2` |
| `CLOVA_OCR_API_URL` | 기존 서비스에서 사용하는 Clova OCR invoke URL |
| `CLOVA_OCR_SECRET_KEY` | 기존 서비스의 Clova OCR 비밀키 |

AWS 인증은 SDK 기본 인증 체인을 사용한다. `AWS_PROFILE`, 환경 변수, 실행 환경의 역할 등을
사용할 수 있다. 단기 자격증명은 `AWS_SESSION_TOKEN`도 함께 설정한다.
키를 명령행 인자, 문서, 결과 파일에 기록하지 않는다.

```powershell
.\gradlew.bat newsletterOcrBatch --args='--input local-newsletter-ocr/input --output local-newsletter-ocr/results --reference-date 2026-10-18 --live'
```

`--live`는 S3 업로드와 Clova OCR을 호출하므로 비용이 발생할 수 있다.
처음에는 소수 문서로 확인한다. API와 S3를 모킹하는 자동 테스트는 외부 비용이 없다.
번역, AI 분석, 문서 중복 검사, 체크리스트나 일정 생성은 수행하지 않는다.

## 기존 라벨의 식별자와 여러 이미지 페이지

다음 내용을 `local-newsletter-ocr/manifest.json`으로 저장한다.
`files`의 상대 경로는 manifest 파일이 있는 폴더를 기준으로 해석한다. 절대 경로도 가능하다.

```json
[
  {
    "sampleId": "pilot_002",
    "files": ["input/건강상태조사서.pdf"]
  },
  {
    "sampleId": "pilot_003",
    "files": ["input/체험학습-1.png", "input/체험학습-2.png"]
  }
]
```

```powershell
.\gradlew.bat newsletterOcrBatch --args='--manifest local-newsletter-ocr/manifest.json --output local-newsletter-ocr/results --reference-date 2026-10-18'
# 대상 확인 후 실제 실행
.\gradlew.bat newsletterOcrBatch --args='--manifest local-newsletter-ocr/manifest.json --output local-newsletter-ocr/results --reference-date 2026-10-18 --live'
```

여러 이미지의 순서는 `files` 배열 순서를 따른다. PDF는 단독 파일로 지정한다.
sampleId는 영문, 숫자, `_`, `-` 조합의 1~100자이며 첫 글자는 영문 또는 숫자여야 한다.
공백이 있는 경로는 `--args` 문자열 안에서 큰따옴표로 감싼다.

## 결과 계약

결과는 `<output>/<sampleId>.json`에 UTF-8 JSON으로 저장한다.

- `schemaVersion`: `gachi-newsletter-ocr-v1`
- `processingVersion`: 캐시 호환성 버전. OCR/정제/결합/날짜 후보 규칙 변경 시 함께 올린다.
- `sampleId`, `sourceFile`: 라벨 식별자와 첫 원본 파일명
- `sourceFiles`: 순서가 있는 파일 목록. 파일명, 로컬 절대 경로, SHA-256 포함
- `fingerprint`: 원본 목록, 해시, 처리 버전, 기준일을 반영한 재사용 식별값
- `status`: `SUCCESS`, `PARTIAL`, `FAILED`
- `errorCode`: 정상 처리 시 null. 오류 원문 대신 안전한 코드만 기록
- `ocrText`: 파싱한 페이지별 OCR 텍스트를 결합한 값
- `input`: `originalText`, `translatedText=null`, `language=KO`, `referenceDate`,
  `timezone=Asia/Seoul`, `dateCandidates`
- `pages`: 문서 전체 페이지 번호, 원본 파일명, 해당 파일 안의 페이지 번호,
  `recognized`, `ocrText`, `originalText`

`input`은 AI 분석 요청의 텍스트 입력으로 사용할 수 있다. AI 레포의 후속 작업에서
동일한 sampleId의 `expected`와 결합한다. 원본 포함 평가는 `sourceFiles.path`로 원본을 찾아
별도로 첨부해야 하며, 이 도구는 만료되는 Presigned URL이나 `expected`를 생성하지 않는다.
AI 레포의 입력 연결 기능이 구현되기 전에는 파일을 평가 스크립트에 바로 넣어 채점하지 않는다.

후보 필드는 `candidateId`, `originalText`, `normalizedDate`, `startOffset`, `endOffset`,
`extractionType`이며 ID는 서비스 요청과 같은 `dc_1`부터 시작한다.
위치는 **Java String의 UTF-16 인덱스**, 끝 위치는 exclusive다.
Python 소비자는 비 BMP 문자(이모지 등)가 있으면 Python 문자열 인덱스와 차이가 날 수 있으므로
후속 입력 어댑터에서 처리해야 한다.

`originalText`는 서비스에서 실제 AI에 보내는 정제된 OCR 값이다. 사람이 수정한 정답 본문과
분리해서 보관한다. `referenceDate`는 문서 발행일이 아니라 평가에서 고정한 업로드 기준일이다.

## 실패와 재실행

- `SUCCESS`: 모든 페이지에서 본문을 인식함
- `PARTIAL / UNREADABLE_PAGE`: 일부 페이지를 읽지 못함. 페이지 번호와 성공한 본문은 유지
- `FAILED / UNREADABLE_PAGE`: 인식 가능한 본문이 없음
- `FAILED / INVALID_INPUT`: 지원하지 않는 형식 또는 PDF가 섞인 다중 파일 입력
- `FAILED / OCR_OR_IO_FAILED`: 파일 접근, S3 업로드/정리, OCR 요청 또는 응답 처리 실패

`PARTIAL`과 `FAILED`는 정상 평가 입력으로 자동 집계하지 않는다.
일부 파일의 오류가 있어도 다음 문서는 처리한다. 결과 파일을 저장할 수 없는 경우는 전체 실행을 중단한다.
인식 실패 페이지의 위치는 보존하며, 결합 본문에서는 빈 페이지를 건너뛰는 서비스 규칙을 따른다.
여러 이미지 중 중간 파일의 통신 실패가 발생하면 그 문서는 FAILED로 기록하고 다음 문서를 처리한다.

같은 명령을 다시 실행하면 원본, 기준일, 처리 버전이 동일한 SUCCESS 결과는 재사용한다.
실패/부분 인식 문서와 변경된 문서만 다시 호출한다. `--force`를 추가하면 성공 결과도 다시 추출한다.
재시도는 문서 단위이며, 부분 실패 문서의 성공 페이지도 다시 호출될 수 있다.

종료 코드는 전체 성공/재사용 또는 dry-run 시 0, FAILED/PARTIAL 발생 시 1,
실행 설정이나 결과 저장 오류 시 2다. Gradle은 자식 프로세스의 비정상 종료를 작업 실패로 표시한다.

## 임시 S3 객체와 보안

임시 키는 `gachi-tools/newsletter-ocr/<UUID>.<확장자>`만 사용한다.
원본 파일명이나 사용자 계정을 S3 키로 사용하지 않는다.
호출 후 성공/실패 여부와 무관하게 업로드한 객체만 삭제하며, 버전 관리 버킷에서는 업로드 버전도 지정한다.
이 경로에만 `PutObject`, `GetObject`, `DeleteObject` 권한을 부여하고, 버전 관리 사용 시
`DeleteObjectVersion`도 허용한다. 버킷 암호화 설정에 따라 KMS 권한이 추가로 필요할 수 있다.
삭제 실패도 정상 성공으로 처리하지 않는다.

강제 종료나 업로드 응답 유실 시 임시 객체가 남을 수 있다. 이 prefix만 대상으로 수명 주기 정리를
별도로 설정하는 것을 권장한다. 도구가 버킷 설정이나 IAM 권한을 자동 변경하지는 않는다.
결과는 비공개 문서 본문과 로컬 경로를 포함하므로 접근을 제한하고 Git에 커밋하지 않는다.

## 자동 테스트

```powershell
.\gradlew.bat test --tests 'com.gachi.be.tools.newsletter.*' --tests '*NewsletterPipelineServiceTest' spotlessCheck
```

OCR, S3는 테스트에서 대체하며 DB 연결이나 실제 API 호출 없이 실행한다.
