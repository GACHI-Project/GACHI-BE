# Children API

## 개요
- 자녀 정보는 로그인 사용자(JWT Bearer) 기준으로만 등록/조회할 수 있다.
- 현재 MVP는 `schoolName` 직접 입력을 사용한다.
- `schoolCode`는 NEIS 연동을 위한 확장 필드이며 현재는 nullable/optional이다.

## 인증
- 헤더: `Authorization: Bearer {accessToken}`
- 토큰이 없거나 유효하지 않으면 `401` 에러를 반환한다.

## 1) 자녀 등록
- `POST /api/v1/children`

### Request Body
```json
{
  "name": "민수",
  "schoolName": "가치초등학교",
  "schoolCode": null,
  "grade": 3,
  "colorCode": "#FF5A5A"
}
```

### Validation Rule
- `name`: 필수, 공백 불가, 최대 50자
- `schoolName`: 필수, 공백 불가, 최대 120자
- `schoolCode`: 선택, 최대 64자 (`NEIS 연동 예정`)
- `grade`: 필수, `1~6`
- `colorCode`: 필수, `#RRGGBB` 형식

### Success Response
```json
{
  "isSuccess": true,
  "status": "CREATED",
  "code": "CHILD2011",
  "message": "자녀 정보 등록에 성공하였습니다.",
  "result": {
    "id": 1,
    "name": "민수",
    "schoolName": "가치초등학교",
    "schoolCode": null,
    "grade": 3,
    "colorCode": "#FF5A5A",
    "createdAt": "2026-04-13T12:00:00"
  }
}
```

## 2) 내 자녀 목록 조회
- `GET /api/v1/children`
- 정렬: `createdAt ASC` (등록 순서 유지)

### Success Response
```json
{
  "isSuccess": true,
  "status": "OK",
  "code": "CHILD2001",
  "message": "내 자녀 목록 조회에 성공하였습니다.",
  "result": [
    {
      "id": 1,
      "name": "민수",
      "schoolName": "가치초등학교",
      "schoolCode": null,
      "grade": 1,
      "colorCode": "#FF5A5A",
      "createdAt": "2026-04-13T12:00:00"
    },
    {
      "id": 2,
      "name": "민수",
      "schoolName": "가치초등학교",
      "schoolCode": null,
      "grade": 6,
      "colorCode": "#00AAFF",
      "createdAt": "2026-04-13T12:01:00"
    }
  ]
}
```

## 에러 정책
- 입력 검증 실패: `400 COMMON4001`
- 액세스 토큰 누락: `401 AUTH4015`
- 액세스 토큰 유효하지 않음: `401 AUTH4016`
- 액세스 토큰 만료: `401 AUTH4017`
