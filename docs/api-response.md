# API Response Standard

## Success
```json
{
  "isSuccess": true,
  "status": "OK",
  "code": "OK200",
  "message": "요청에 성공하였습니다.",
  "result": {
    "testString": "api 테스트 중입니다."
  }
}
```

## Fail
```json
{
  "isSuccess": false,
  "status": "BAD_REQUEST",
  "code": "COMMON4001",
  "message": "입력값이 올바르지 않습니다."
}
```
