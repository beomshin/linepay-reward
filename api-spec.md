# API 명세 (api-spec.md)

## 1. 공통 규격

| 항목 | 규격 |
|---|---|
| Base Path | `/linepay/{version}/{mission \| reward}` |
| 버전 | `v1` |
| Content-Type | `application/json; charset=UTF-8` |
| 메서드 | 조회 `GET`, 미션 완료 처리·보상 지급 요청 `POST` |
| 사용자 식별 | PathVariable `{userId}` (인증 없음, 과제 11절) |
| 시간 표기 | KST 기준, 일자 `yyyyMMdd` / 시간 `HHmmss` 필드 분리 |
| 식별 번호 | 참여 이력은 이력번호 `participationNo`(`PT`+일자 8자리+일련번호 10자리), 보상 결과는 리워드번호 `rewardNo`(`RW`+일자+일련번호)로 식별. DB PK는 API에 노출하지 않음 |
| 요청 추적 | 모든 응답 헤더에 `X-Trace-Id`(요청별 traceId, 32자리) 포함. 서버 로그의 `[traceId]`와 같은 값 |

### 1.1 요청값 검증 (Spring Validation)

| 경로 변수 | 규칙 | 위반 시 code |
|---|---|---|
| `userId`, `missionId` | 필수 (공백 불가) | MISSING_REQUIRED_VALUE |
| `participationNo` | 필수 (공백 불가) | MISSING_REQUIRED_VALUE |

- 검증에 걸린 항목이 여러 개면 첫 번째 항목 하나만 응답합니다.
- 검증에 실패한 요청은 서비스 로직까지 전달되지 않습니다.
- `userId`, `missionId`에는 형식·길이 제한이 없습니다. 특수문자가 들어가거나 긴 값도 검증을 통과하고, 존재하지 않으면 `USER_NOT_FOUND` / `MISSION_NOT_FOUND`(404)로 응답합니다.
- `userId;abc`처럼 세미콜론 뒤 값은 Spring MVC가 matrix variable로 보고 잘라냅니다.

### 1.2 응답 형식

모든 응답은 `code`, `msg`, `data`를 포함합니다. 실패 응답의 `data`는 `null`입니다.

```json
{ "code": "0000", "msg": "SUCCESS", "data": { } }
```

```json
{ "code": "MISSION_NOT_FOUND", "msg": "미션을 찾을 수 없습니다.", "data": null }
```

- 성공: `code = "0000"`, `msg = "SUCCESS"`
- 실패: `code` = 영문 사유 코드(ErrorCode enum 이름), `msg` = 한글 메시지
- 요청값 검증 실패 시 `msg` 끝에 문제가 된 필드명을 붙입니다. (예: `"필수 요청값이 누락되었습니다. (participationNo)"`)

## 2. API 목록

| # | Method | Path | 설명 |
|---|---|---|---|
| 1 | GET | `/linepay/v1/mission/{userId}` | 특정 사용자가 참여 가능한 미션 목록 조회 |
| 2 | POST | `/linepay/v1/mission/{userId}/{missionId}/complete` | 미션 수행 완료 처리 (참여 이력 생성) |
| 3 | POST | `/linepay/v1/reward/{userId}/{participationNo}` | 완료된 미션 참여 이력에 대한 보상 지급 요청 |
| 4 | GET | `/linepay/v1/reward/{userId}/{participationNo}` | 보상 지급 결과 조회 |

---

### 2.1 참여 가능한 미션 목록 조회

`GET /linepay/v1/mission/{userId}`

조회 시점에 해당 사용자가 **실제로 참여할 수 있는** 미션만 반환합니다.
(참여 기간 내, 미션 전체 100회 미만, 사용자 당일 10회 미만, 직전 참여 후 1시간 경과)

> 교정 5: 오늘 참여 기간에 걸친 미션 목록은 일자별로 캐싱합니다(TTL 10분, 미션 데이터 변경은 캐시 만료 후 반영). 참여 횟수·재참여 조건은 요청마다 DB에서 확인하므로 응답 내용은 캐싱 전과 같습니다.

**Response 200**
```json
{
  "code": "0000",
  "msg": "SUCCESS",
  "data": {
    "userId": "USER_0001",
    "missions": [
      {
        "missionId": "MISSION_0002",
        "missionType": "RANDOM_BOX",
        "title": "랜덤 박스 열고 포인트 또는 쿠폰 받기",
        "entryStartDate": "20260501",
        "entryStartTime": "000000",
        "entryEndDate": "20270101",
        "entryEndTime": "000000"
      },
      {
        "missionId": "MISSION_0003",
        "missionType": "RANDOM_BOX",
        "title": "랜덤 박스 열고 포인트 받기",
        "entryStartDate": "20260501",
        "entryStartTime": "000000",
        "entryEndDate": "20270101",
        "entryEndTime": "000000"
      }
    ]
  }
}
```

| HTTP | code | msg | 상황 |
|---|---|---|---|
| 400 | MISSING_REQUIRED_VALUE | 필수 요청값이 누락되었습니다. (userId) | `userId`가 공백 |
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없습니다. | 존재하지 않는 사용자 |

---

### 2.2 미션 수행 완료 처리

`POST /linepay/v1/mission/{userId}/{missionId}/complete` (Request Body 없음)

참여 조건을 만족하면 미션 참여 이력을 하나 생성하고 이력번호 `participationNo`를 반환합니다.
완료 요청이 받아들여진 시각이 참여 시각이 되며, 재참여 가능 시점(1시간)의 기준이 됩니다.

**Response 200**
```json
{
  "code": "0000",
  "msg": "SUCCESS",
  "data": {
    "participationNo": "PT202609010000000001",
    "missionId": "MISSION_0003",
    "userId": "USER_0001",
    "participatedDate": "20260901",
    "participatedTime": "120000"
  }
}
```

| HTTP | code | msg | 상황 |
|---|---|---|---|
| 400 | MISSING_REQUIRED_VALUE | 필수 요청값이 누락되었습니다. (userId) | `userId`가 공백 |
| 400 | MISSING_REQUIRED_VALUE | 필수 요청값이 누락되었습니다. (missionId) | `missionId`가 공백 |
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없습니다. | 존재하지 않는 사용자 |
| 404 | MISSION_NOT_FOUND | 미션을 찾을 수 없습니다. | 존재하지 않는 미션 |
| 409 | MISSION_NOT_IN_PERIOD | 미션 참여 가능 기간이 아닙니다. | `entry_start_at <= 현재 < entry_end_at` 불만족 |
| 409 | MISSION_TOTAL_LIMIT_EXCEEDED | 미션 전체 참여 횟수를 초과했습니다. | 미션 전체 참여 100회 도달 |
| 409 | MISSION_DAILY_LIMIT_EXCEEDED | 오늘 이 미션에 참여할 수 있는 횟수를 모두 사용했습니다. | 사용자의 같은 미션 당일(KST) 참여 10회 도달 |
| 409 | MISSION_REENTRY_COOLDOWN | 직전 참여 후 1시간이 지나야 다시 참여할 수 있습니다. | 직전 참여 후 1시간 미경과 |

> 여러 조건을 동시에 위반하면 기간 → 전체 횟수 → 일별 횟수 → 재참여 간격 순서로 첫 번째 사유를 반환합니다.

---

### 2.3 보상 지급 요청

`POST /linepay/v1/reward/{userId}/{participationNo}` (Request Body 없음)

해당 참여 이력에 대해 지급 가능한 보상 아이템 중 하나를 무작위로 선택해 지급합니다.

- 리워드 포인트: 5 이상 10 이하 무작위 값
- 쿠폰: 외부 쿠폰 시스템에서 1개 발급 (한도 소진·발급 중지 템플릿은 후보에서 제외)
- 지급 가능한 보상이 없으면 `rewardStatus = NO_REWARD`로 **정상 응답(200)** 하며, 같은 참여 이력으로 다시 요청할 수 있습니다.
- 쿠폰 API 호출 중 IO 오류·타임아웃이 나면 503 `COUPON_COMMUNICATION_FAILED`, 예상하지 못한 응답이나 그 외 예외가 나면 500 `COUPON_SYSTEM_ERROR`로 응답합니다. 두 경우 모두 보상 결과를 `rewardStatus = FAILED`(실패 사유 `failureReason` 포함)로 저장하며, 같은 이력번호로 다시 요청할 수 있고 리워드번호는 그대로 유지됩니다.

**Response 200 - 포인트 지급**
```json
{
  "code": "0000",
  "msg": "SUCCESS",
  "data": {
    "rewardNo": "RW202609010000000001",
    "participationNo": "PT202609010000000001",
    "missionId": "MISSION_0003",
    "userId": "USER_0001",
    "rewardStatus": "GRANTED",
    "missionItemId": "ITEM_0004",
    "itemType": "REWARD_POINT",
    "pointAmount": 7,
    "couponTemplateId": null,
    "couponId": null,
    "processedDate": "20260901",
    "processedTime": "120000",
    "failureReason": null
  }
}
```

**Response 200 - 쿠폰 지급**
```json
{
  "code": "0000",
  "msg": "SUCCESS",
  "data": {
    "rewardNo": "RW202609010000000002",
    "participationNo": "PT202609010000000002",
    "missionId": "MISSION_0002",
    "userId": "USER_0002",
    "rewardStatus": "GRANTED",
    "missionItemId": "ITEM_0003",
    "itemType": "COUPON",
    "pointAmount": null,
    "couponTemplateId": "COUPON_TEMPLATE_0001",
    "couponId": "COUPON_0001",
    "processedDate": "20260901",
    "processedTime": "120000",
    "failureReason": null
  }
}
```

**Response 200 - 지급 가능한 보상 없음**
```json
{
  "code": "0000",
  "msg": "SUCCESS",
  "data": {
    "rewardNo": "RW202609010000000003",
    "participationNo": "PT202609010000000003",
    "missionId": "MISSION_X",
    "userId": "USER_0001",
    "rewardStatus": "NO_REWARD",
    "missionItemId": null,
    "itemType": null,
    "pointAmount": null,
    "couponTemplateId": null,
    "couponId": null,
    "processedDate": "20260901",
    "processedTime": "120000",
    "failureReason": null
  }
}
```

| HTTP | code | msg | 상황 |
|---|---|---|---|
| 400 | MISSING_REQUIRED_VALUE | 필수 요청값이 누락되었습니다. (userId) | `userId`가 공백 |
| 400 | MISSING_REQUIRED_VALUE | 필수 요청값이 누락되었습니다. (participationNo) | `participationNo`가 공백 |
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없습니다. | 존재하지 않는 사용자 |
| 404 | PARTICIPATION_NOT_FOUND | 미션 참여 이력을 찾을 수 없습니다. | 이력번호가 없거나 요청 사용자의 이력이 아님 (DB PK 숫자로는 조회되지 않음) |
| 409 | REWARD_ALREADY_GRANTED | 이미 보상이 지급된 미션 참여입니다. | 이미 보상이 지급된 참여 이력 |
| 500 | COUPON_SYSTEM_ERROR | 쿠폰 발급 처리 중 오류가 발생했습니다. | 쿠폰 시스템이 예상하지 못한 결과를 반환하거나 호출 중 그 외 예외 발생 (보상 결과는 `FAILED`로 저장) |
| 503 | COUPON_COMMUNICATION_FAILED | 쿠폰 시스템과 통신하지 못해 보상을 지급하지 못했습니다. 잠시 후 다시 요청해 주세요. | 쿠폰 시스템 IO 오류·타임아웃 (보상 결과는 `FAILED`로 저장) |

---

### 2.4 보상 지급 결과 조회

`GET /linepay/v1/reward/{userId}/{participationNo}`

마지막 보상 지급 요청 결과를 반환합니다. 응답 `data`는 2.3과 동일합니다.

| HTTP | code | msg | 상황 |
|---|---|---|---|
| 400 | MISSING_REQUIRED_VALUE | 필수 요청값이 누락되었습니다. (userId) | `userId`가 공백 |
| 400 | MISSING_REQUIRED_VALUE | 필수 요청값이 누락되었습니다. (participationNo) | `participationNo`가 공백 |
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없습니다. | 존재하지 않는 사용자 |
| 404 | PARTICIPATION_NOT_FOUND | 미션 참여 이력을 찾을 수 없습니다. | 이력번호가 없거나 요청 사용자의 이력이 아님 (DB PK 숫자로는 조회되지 않음) |
| 404 | REWARD_NOT_FOUND | 보상 지급 요청 이력이 없습니다. | 아직 보상 지급 요청을 하지 않은 참여 이력 |

## 3. 에러 코드 전체 목록

| HTTP | code | msg |
|---|---|---|
| 400 | MISSING_REQUIRED_VALUE | 필수 요청값이 누락되었습니다. |
| 400 | INVALID_FORMAT | 요청값 형식이 올바르지 않습니다. (현재 API에서는 발생하지 않음: 숫자형 경로 변수 없음) |
| 400 | OUT_OF_RANGE | 요청값이 허용 범위를 벗어났습니다. (현재 API에서는 발생하지 않음) |
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없습니다. |
| 404 | MISSION_NOT_FOUND | 미션을 찾을 수 없습니다. |
| 404 | PARTICIPATION_NOT_FOUND | 미션 참여 이력을 찾을 수 없습니다. |
| 404 | REWARD_NOT_FOUND | 보상 지급 요청 이력이 없습니다. |
| 404 | API_NOT_FOUND | 요청한 API를 찾을 수 없습니다. |
| 405 | METHOD_NOT_ALLOWED | 지원하지 않는 HTTP 메서드입니다. |
| 409 | MISSION_NOT_IN_PERIOD | 미션 참여 가능 기간이 아닙니다. |
| 409 | MISSION_TOTAL_LIMIT_EXCEEDED | 미션 전체 참여 횟수를 초과했습니다. |
| 409 | MISSION_DAILY_LIMIT_EXCEEDED | 오늘 이 미션에 참여할 수 있는 횟수를 모두 사용했습니다. |
| 409 | MISSION_REENTRY_COOLDOWN | 직전 참여 후 1시간이 지나야 다시 참여할 수 있습니다. |
| 409 | REWARD_ALREADY_GRANTED | 이미 보상이 지급된 미션 참여입니다. |
| 500 | COUPON_SYSTEM_ERROR | 쿠폰 발급 처리 중 오류가 발생했습니다. |
| 500 | INTERNAL_SERVER_ERROR | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. |
| 503 | COUPON_COMMUNICATION_FAILED | 쿠폰 시스템과 통신하지 못해 보상을 지급하지 못했습니다. 잠시 후 다시 요청해 주세요. |
| 503 | DATABASE_ERROR | 데이터 처리 중 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. |

모든 API는 DB 장애(연결 실패, 락 대기 초과 등) 시 503 `DATABASE_ERROR`로 응답합니다.

에러 코드는 `ErrorCode` enum으로 정의하고, `GlobalExceptionHandler`(`@RestControllerAdvice`)에서 한 번에 처리합니다.

## 4. 외부 쿠폰 시스템 (재현)

실제 HTTP 서버는 만들지 않았고, 계약(과제 8절)을 `CouponClient` 인터페이스로 정의한 뒤 In-memory 구현 `FakeCouponSystem`이 동작을 재현합니다.

| 계약 | 재현 메서드 | 실패 결과 |
|---|---|---|
| `GET /coupon-templates/{couponTemplateId}` | `getCouponTemplate` | `TEMPLATE_NOT_FOUND`(404) |
| `POST /coupon-issues` | `issueCoupon` | `QUANTITY_EXHAUSTED`(409), `INVALID_TEMPLATE`(400), `REQUEST_ID_CONFLICT`(409) |
| `GET /coupon-issues/{requestId}` | `getCouponIssue` | `ISSUE_NOT_FOUND`(404) |

리워드 서비스는 쿠폰 발급 `requestId`로 `REWARD_{participationNo}_{couponTemplateId}`를 사용합니다.
