# 설계 문서 (DESIGN.md)

## 1. 전체 설계

### 1.1 구성요소

```
[Client]
   │  /linepay/v1/mission/**, /linepay/v1/reward/**
   ▼
[Controller]  MissionController, RewardController
   │   (공통 응답 ApiResponse / 예외 → GlobalExceptionHandler + ErrorCode)
   ▼
[Service]     MissionService ──► ParticipationPolicy (참여 조건 규칙)
              RewardService  ──► RewardRandomizer (무작위 선택·포인트)
   │                         └─► CouponClient (외부 쿠폰 계약)
   ▼                                  └─ FakeCouponSystem (In-memory 재현체)
[Repository]  Spring Data JPA
   ▼
[H2]          users, mission, mission_item, mission_participation, reward
```

| 패키지 | 역할 |
|---|---|
| `common.response` | 공통 응답 `{code, msg, data}` (실패 시 code=영문 사유 코드, msg=한글 메시지) |
| `common.exception` | `ErrorCode` enum, `BusinessException`, `@RestControllerAdvice` 전역 처리 |
| `common.time` | KST 변환, 일자(`yyyyMMdd`)/시간(`HHmmss`) 포맷 |
| `common.config` | 서비스 기준 시각 `Clock` 빈 (고정 시각 또는 시스템 시각) |
| `user` | 사용자 엔티티, 존재 여부 검증 |
| `mission` | 미션·보상 아이템·참여 이력 엔티티, 참여 정책, 미션 API |
| `reward` | 보상 결과 엔티티, 보상 선정·지급 로직, 보상 API |
| `coupon` | 외부 쿠폰 시스템 계약(`CouponClient`)과 재현체(`FakeCouponSystem`) |

### 1.2 데이터 모델

| 테이블 | 주요 컬럼 | 비고 |
|---|---|---|
| `users` | user_id(PK), user_name | `user`는 H2 예약어 |
| `mission` | mission_id(PK), mission_type, title, entry_start_date/time, entry_end_date/time | 기간을 일자/시간 컬럼으로 분리 |
| `mission_item` | mission_item_id(PK), mission_id, item_type, coupon_template_id | COUPON일 때만 템플릿 ID |
| `mission_participation` | participation_id(PK, identity), mission_id, user_id, participated_date, participated_time | 인덱스 (mission_id, user_id, participated_date) |
| `reward` | reward_id(PK), participation_id(**UNIQUE**), reward_status, mission_item_id, item_type, point_amount, coupon_template_id, coupon_id, coupon_request_id, processed_date/time | 참여 이력 1건당 최대 1행 |

- 모든 일시는 KST이며, 일자와 시간을 컬럼으로 나눠 저장합니다.
- 일별 참여 횟수는 `participated_date`(KST 일자)가 같은 이력의 수로 계산합니다. 이렇게 하면 "KST 당일 00:00:00 이상, 다음 날 00:00:00 미만" 규칙과 같아집니다.
- 리워드 포인트는 서비스 내부에서 관리하므로 지급한 포인트 값을 `reward.point_amount`에 기록합니다.

### 1.3 시간 처리

- `Clock` 빈 하나로 모든 "현재 시각"을 얻습니다. 기본값은 과제 9절 검증 기준 시각(2026-09-01T12:00:00+09:00)으로 고정했습니다.
- `KstTime.now(clock)`은 KST로 변환한 뒤 나노초를 잘라 초 단위로 맞춥니다. 저장 형식인 `HHmmss`와 같은 정밀도입니다.
- 테스트에서는 `MutableClock`으로 교체해 1시간 경과, 자정 경계, 기간 경계를 검증합니다.

## 2. 비즈니스 규칙 구현

### 2.1 미션 참여 (과제 6절)

`ParticipationPolicy.check()`가 규칙을 한곳에 모읍니다. **목록 조회와 완료 처리가 같은 규칙을 씁니다.**

| 순서 | 규칙 | 실패 코드 |
|---|---|---|
| 1 | `entry_start_at <= now < entry_end_at` | MISSION_NOT_IN_PERIOD |
| 2 | 미션 전체 참여 < 100 | MISSION_TOTAL_LIMIT_EXCEEDED |
| 3 | 사용자 당일(KST) 참여 < 10 | MISSION_DAILY_LIMIT_EXCEEDED |
| 4 | `now >= 직전 참여 시각 + 1시간` | MISSION_REENTRY_COOLDOWN |

- 재참여 기준 시각은 직전 완료 요청이 받아들여진 시각(= 참여 이력의 `participated_date/time`)입니다.
- 1시간 제한은 날짜가 바뀌어도 유지합니다. (예: 23:30에 참여했다면 다음 날 00:10에는 참여 불가)
- 거절된 요청은 이력을 만들지 않으므로 참여 횟수에 들어가지 않습니다.

### 2.2 보상 지급 (과제 7절)

```
1. 참여 이력 행 락 획득 (소유자 확인)
2. reward 행 확인 → GRANTED이면 409 REWARD_ALREADY_GRANTED
3. [복구] 쿠폰 아이템별로 외부 발급 결과(GET /coupon-issues/{requestId}) 확인
         → 이미 발급된 쿠폰이 있으면 그 쿠폰으로 GRANTED 처리
4. 후보 = 포인트 아이템 + 발급 가능한 쿠폰 아이템(템플릿 AVAILABLE이고 한도가 남음)
5. 후보 중 무작위 선택
   - 포인트 → 5~10 무작위 지급
   - 쿠폰   → 외부 발급. 한도 소진/유효하지 않은 템플릿으로 거절되면 후보에서 빼고 5로 돌아감
6. 후보가 비면 NO_REWARD 기록 (같은 참여 이력으로 재요청 가능)
```

- 쿠폰 발급 `requestId = REWARD_{participationId}_{couponTemplateId}`
  같은 참여 이력을 다시 시도하면 외부 시스템이 기존 결과를 돌려주므로 쿠폰이 중복 발급되지 않습니다.
- 템플릿 조회(4)와 발급(5) 사이에 한도가 소진될 수 있으므로, 발급이 거절되면 다른 후보로 다시 선정합니다.
- 예상하지 못한 외부 오류(`REQUEST_ID_CONFLICT` 등)는 500 `COUPON_SYSTEM_ERROR`로 응답하고 트랜잭션을 롤백합니다. 보상 결과는 저장하지 않습니다.

### 2.3 반복·동시 요청 (과제 5절)

과제 5절에 "같은 요청이 반복되거나 여러 요청이 비슷한 시점에 전달될 수 있다"고 명시되어 있어, 참여·보상 정책이 동시 요청에서도 지켜지도록 구현했습니다.

| 대상 | 방법 | 보장 내용 |
|---|---|---|
| 미션 완료 처리 | `mission` 행 `PESSIMISTIC_WRITE` 락 후 "검사 → 이력 생성" | 전체 100회, 일 10회, 1시간 제한 |
| 보상 지급 요청 | `mission_participation` 행 `PESSIMISTIC_WRITE` 락 후 "지급 여부 확인 → 지급" | 참여 이력 1건당 1회 지급 |
| 보상 결과 | `reward.participation_id` UNIQUE | 락이 빠져도 DB에서 한 번 더 막음 |
| 쿠폰 발급 | 결정적인 `requestId`(멱등 키) | 재시도해도 쿠폰 1개 |

선택하지 않은 방법

- **낙관적 락(@Version)**: 충돌이 잦은 "같은 미션 동시 참여" 상황에서는 재시도 로직이 필요하고 실패가 늘어납니다. 전체 횟수(100회)처럼 여러 행을 집계하는 규칙은 단일 행 버전으로 보호하기 어렵습니다.
- **애플리케이션 락(synchronized, 분산 락)**: 인스턴스가 여러 대면 synchronized는 동작하지 않습니다. 분산 락은 외부 인프라(Redis 등)가 필요해 과제 실행 조건(외부 인프라 없음)과 맞지 않습니다.
- **카운터 컬럼 원자적 증가**: 전체 횟수에는 효과적이지만, 일별 횟수와 1시간 제한까지 한 번에 보장하기 어렵습니다.

## 3. 요청값 검증과 코드 규칙 (교정 1)

- **요청값 검증:** 컨트롤러 경로 변수에 Bean Validation 제약(`userId`·`missionId`는 `@NotBlank`, `participationId`는 `@NotNull`·`@Positive`)을 선언했습니다. `userId`·`missionId`에는 형식·길이 제한을 두지 않고, 존재하지 않는 값은 서비스의 존재 여부 확인에서 404로 거절합니다. Spring MVC 내장 메서드 검증이 `HandlerMethodValidationException`을 던지면 전역 핸들러가 제약 종류에 따라 `MISSING_REQUIRED_VALUE` / `INVALID_FORMAT` / `OUT_OF_RANGE`(400)로 바꿉니다. 검증에 실패하면 서비스 로직까지 가지 않습니다.
- **에러 응답:** 모든 실패 응답은 `code` = 영문 사유 코드(ErrorCode enum 이름), `msg` = 한글 메시지입니다. HTTP 상태 코드는 그대로입니다.
- **Lombok:** 엔티티는 `@Getter` + `@NoArgsConstructor(access = PROTECTED)`만 씁니다. `@Data`·`@Setter`는 쓰지 않아 상태 변경은 도메인 메서드(`grantPoint`, `markNoReward` 등)로만 합니다.
- **의존성 주입:** 모든 빈이 `private final` 필드 + `@RequiredArgsConstructor` 생성자 주입을 씁니다.

## 4. 외부 쿠폰 시스템 재현

- `CouponClient` 인터페이스가 과제 8절 계약(8.1 템플릿 조회, 8.2 발급, 8.3 발급 결과 조회)을 그대로 표현합니다.
- `FakeCouponSystem`은 계약의 모든 결과를 재현합니다. 성공, 404, 한도 소진, 유효하지 않은 템플릿(미존재·발급 중지), 동일 requestId 멱등, 동일 requestId에 다른 내용이면 거부.
- 외부 시스템 한 대를 흉내 내므로 상태 변경 메서드를 `synchronized`로 원자 처리했습니다.
- 실제 연동할 때는 HTTP 구현체로 `CouponClient`만 교체하면 됩니다.

## 5. 중요 문제와 우선순위 (과제 12·14절)

> 이번 작업 범위(최초 프롬프트)에서는 과제 12·14절 "추가로 발견한 문제" 선정과 작성을 제외했습니다. 후속 작업에서 작성할 예정입니다.
>
> 참고로 2.3절의 동시성 제어와 쿠폰 멱등 처리는 과제 5절(반복·동시 요청)과 7절(참여 이력당 보상 1회) 정책을 지키기 위한 **기본 구현 범위**로 보고 구현했습니다.

## 6. 검증 방법

- JUnit5 자동화 테스트 73개 (단위 19 / 통합 31 / API 23)
  - 단위: 참여 정책 경계값, KST 변환, 쿠폰 재현체 계약
  - 통합: 실제 H2·트랜잭션·락을 쓰는 미션/보상 시나리오, 시간 경과(MutableClock)
  - 동시성: 스레드 여러 개로 동시 요청 → 결과 건수 검증
  - API: MockMvc로 응답 포맷, HTTP 상태, 에러 코드, 요청값 검증(필수·형식·범위) 확인
- QA 항목과 테스트 매핑: `test-or-verification/QA_LIST.md`
- 실행 결과: `test-or-verification/TEST_RESULT.md`

## 7. 현재 구현의 한계와 추가 개선

- 같은 미션에 대한 완료 처리가 미션 행 락으로 직렬 처리되어, 참여가 몰리는 미션에서는 처리량이 제한됩니다.
- 외부 쿠폰 호출이 DB 트랜잭션 안에서 이뤄져, 외부 응답이 지연되면 락 점유 시간이 길어집니다. (개선 방향: 보상 상태를 PENDING으로 먼저 커밋한 뒤 트랜잭션 밖에서 발급하고 결과를 반영)
- 목록 조회는 미션마다 집계 쿼리를 실행합니다. 미션 수가 많아지면 한 번에 집계하는 쿼리로 바꿔야 합니다.
- 인증이 없어 `userId`만 알면 다른 사용자로 요청할 수 있습니다. (과제 11절 가정)
