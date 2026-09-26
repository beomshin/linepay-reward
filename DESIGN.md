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
| `mission` | mission_id(PK), mission_type, title, entry_start_date/time, entry_end_date/time | 기간을 일자/시간 컬럼으로 분리, 인덱스 `idx_mission_entry_period`(entry_start_date, entry_end_date) |
| `mission_item` | mission_item_id(PK), mission_id, item_type, coupon_template_id | COUPON일 때만 템플릿 ID |
| `mission_participation` | participation_id(PK, 내부용), **participation_no(이력번호, UNIQUE)**, mission_id, user_id, participated_date, participated_time | 인덱스 `idx_participation_mission_user_datetime`(mission_id, user_id, participated_date, participated_time) |
| `reward` | reward_id(PK, 내부용), **reward_no(리워드번호, UNIQUE)**, participation_no(**UNIQUE**), reward_status, mission_item_id, item_type, point_amount, coupon_template_id, coupon_id, coupon_request_id, processed_date/time | 참여 이력 1건당 최대 1행 |

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

- 쿠폰 발급 `requestId = REWARD_{participationNo}_{couponTemplateId}`
  같은 참여 이력을 다시 시도하면 외부 시스템이 기존 결과를 돌려주므로 쿠폰이 중복 발급되지 않습니다.
- 템플릿 조회(4)와 발급(5) 사이에 한도가 소진될 수 있으므로, 발급이 거절되면 다른 후보로 다시 선정합니다.
- 예상하지 못한 외부 오류(`REQUEST_ID_CONFLICT` 등)는 500 `COUPON_SYSTEM_ERROR`로 응답하고 트랜잭션을 롤백합니다. 보상 결과는 저장하지 않습니다.

### 2.3 반복·동시 요청 (과제 5절)

과제 5절에 "같은 요청이 반복되거나 여러 요청이 비슷한 시점에 전달될 수 있다"고 명시되어 있어, 참여·보상 정책이 동시 요청에서도 지켜지도록 구현했습니다.

| 대상 | 방법 | 보장 내용 |
|---|---|---|
| 미션 완료 처리 | `mission` 행 `PESSIMISTIC_WRITE` 락 후 "검사 → 이력 생성" | 전체 100회, 일 10회, 1시간 제한 |
| 보상 지급 요청 | `mission_participation` 행 `PESSIMISTIC_WRITE` 락 후 "지급 여부 확인 → 지급" | 참여 이력 1건당 1회 지급 |
| 보상 결과 | `reward.participation_no` UNIQUE | 락이 빠져도 DB에서 한 번 더 막음 |
| 쿠폰 발급 | 결정적인 `requestId`(멱등 키) | 재시도해도 쿠폰 1개 |

선택하지 않은 방법

- **낙관적 락(@Version)**: 충돌이 잦은 "같은 미션 동시 참여" 상황에서는 재시도 로직이 필요하고 실패가 늘어납니다. 전체 횟수(100회)처럼 여러 행을 집계하는 규칙은 단일 행 버전으로 보호하기 어렵습니다.
- **애플리케이션 락(synchronized, 분산 락)**: 인스턴스가 여러 대면 synchronized는 동작하지 않습니다. 분산 락은 외부 인프라(Redis 등)가 필요해 과제 실행 조건(외부 인프라 없음)과 맞지 않습니다.
- **카운터 컬럼 원자적 증가**: 전체 횟수에는 효과적이지만, 일별 횟수와 1시간 제한까지 한 번에 보장하기 어렵습니다.

## 3. 요청값 검증과 코드 규칙 (교정 1)

- **요청값 검증:** 컨트롤러 경로 변수에 Bean Validation 제약(`userId`·`missionId`는 `@NotBlank`, `participationNo`도 `@NotBlank`)을 선언했습니다. (교정 3 이후 숫자형 경로 변수가 없어 `INVALID_FORMAT`·`OUT_OF_RANGE`는 현재 API에서 발생하지 않습니다.) `userId`·`missionId`에는 형식·길이 제한을 두지 않고, 존재하지 않는 값은 서비스의 존재 여부 확인에서 404로 거절합니다. Spring MVC 내장 메서드 검증이 `HandlerMethodValidationException`을 던지면 전역 핸들러가 제약 종류에 따라 `MISSING_REQUIRED_VALUE` / `INVALID_FORMAT` / `OUT_OF_RANGE`(400)로 바꿉니다. 검증에 실패하면 서비스 로직까지 가지 않습니다.
- **에러 응답:** 모든 실패 응답은 `code` = 영문 사유 코드(ErrorCode enum 이름), `msg` = 한글 메시지입니다. HTTP 상태 코드는 그대로입니다.
- **Lombok:** 엔티티는 `@Getter` + `@NoArgsConstructor(access = PROTECTED)`만 씁니다. `@Data`·`@Setter`는 쓰지 않아 상태 변경은 도메인 메서드(`grantPoint`, `markNoReward` 등)로만 합니다.
- **의존성 주입:** 모든 빈이 `private final` 필드 + `@RequiredArgsConstructor` 생성자 주입을 씁니다.

## 4. 운영 환경 대응 (교정 2)

- **환경 설정 분리:** `application.yml`(공통) + `application-{local,dev,prod}.yml`. 프로파일을 지정하지 않으면 local로 기동합니다. 기준 시각 고정(`linepay.clock.fixed-at`)과 H2 콘솔 같은 테스트용 설정은 local에만 두고 prod에는 두지 않습니다. dev도 기준 시각을 고정하지 않습니다.
- **로그 출력:** `logback-spring.xml`에서 prod가 아니면 콘솔, prod면 일자별 롤링 파일(`${logging.file.path}/linepay-reward.log`, 30일 보관)로 출력합니다.
- **요청 추적:** `TraceIdFilter`(`OncePerRequestFilter`, 최우선 순서)가 요청마다 traceId를 MDC에 넣고 응답 헤더 `X-Trace-Id`로 돌려줍니다. 요청·응답·예외 로그를 남기고, 요청이 끝나면(예외 포함) `finally`에서 MDC 값을 지웁니다. WAS가 스레드를 재사용해도 이전 요청의 traceId가 섞이지 않습니다.
- **로그 접두어:** `[REQ]`/`[RES]`/`[ERR]`(필터), `[EXC]`(전역 예외 처리), `[USER]`/`[MISSION]`/`[REWARD]`/`[COUPON]`(DB 조회·비즈니스 단계). 메시지는 한글로 남깁니다.
- **운영 로그 강화:** API 요청이 들어온 뒤 DB 조회(사용자 확인, 미션 락, 참여 수 집계, 기존 보상 조회, 보상 아이템 조회, 저장)와 주요 로직(참여 조건 판정, 쿠폰 템플릿 확인·발급, 보상 선택)마다 INFO 로그를 남겨, 운영 로그 파일만으로 요청 하나의 처리 흐름을 따라갈 수 있게 했습니다.

## 5. 데이터 모델링 및 조회 최적화 (교정 3)

**비즈니스 키**

| 키 | 형식 | 채번 | 제약 |
|---|---|---|---|
| 이력번호 `participation_no` | `PT` + 일자(yyyyMMdd) + 일련번호 10자리 (예: `PT202609010000000001`) | DB 시퀀스 `participation_no_seq` | `uk_participation_no` |
| 리워드번호 `reward_no` | `RW` + 일자 + 일련번호 10자리 | DB 시퀀스 `reward_no_seq` | `uk_reward_no` |

- 일련번호는 DB 시퀀스(`schema.sql`)에서 받습니다. 시퀀스는 동시에 여러 요청이 와도 같은 값을 주지 않으므로 애플리케이션 락 없이 중복 없는 번호를 만들 수 있고, 유니크 제약조건으로 DB에서 한 번 더 막습니다.
- API·로직·연관은 모두 비즈니스 키 기준입니다. 보상 API 경로 변수, 응답, `reward`→참여 이력 연결(`participation_no`), 쿠폰 발급 requestId가 이력번호를 씁니다. PK는 DB 내부 식별용으로만 남기고 API에 노출하지 않습니다.
- 리워드번호는 첫 보상 요청 때 채번합니다. `NO_REWARD` 후 재요청하면 같은 리워드번호를 그대로 씁니다.
- 한계: 시퀀스 값은 롤백돼도 되돌아가지 않아 번호 중간에 빈 값이 생길 수 있습니다. 일자 부분은 채번 시각(KST) 기준입니다.

**조회 메서드와 인덱스**

| Repository 메서드 | 방식 | 조건 | 사용 인덱스 |
|---|---|---|---|
| `countByMissionId` | JPA 메소드명 | mission_id | `idx_participation_mission_user_datetime` (선두 컬럼) |
| `countDailyByUser` | JPQL | mission_id, user_id, participated_date | `idx_participation_mission_user_datetime` |
| `findRecentByUser` (+`findLatestByUser`, 1건) | JPQL | mission_id, user_id, 일자·시간 역순 | `idx_participation_mission_user_datetime` |
| `findByParticipationNo` | JPA 메소드명 | participation_no | `uk_participation_no` |
| `findByParticipationNoForUpdate` | JPQL + 비관적 락 | participation_no | `uk_participation_no` |
| `RewardRepository.findByParticipationNo` | JPA 메소드명 | participation_no | `uk_reward_participation_no` |
| `MissionItemRepository.findByMission` | JPQL | mission_id, **정렬 없음** | `idx_mission_item_mission` |
| `MissionRepository.findEntryPeriodMissions` | JPQL | entry_start_date ≤ 오늘 ≤ entry_end_date | `idx_mission_entry_period` |

- 메서드명이 짧고 의미가 분명한 조회(`countByMissionId`, `findByParticipationNo`)는 JPA 메소드명 쿼리를 그대로 쓰고, 조건이 길어 메서드명이 과도해지는 조회만 JPQL로 바꿨습니다.
- 인덱스 사용 여부는 H2 `EXPLAIN`으로 한 번 확인했으며(결과는 `TEST_RESULT.md`), 자동화 테스트로는 두지 않았습니다.
- 전체 미션 조회(`findAllByOrderByMissionIdAsc`)는 오늘 참여 기간에 걸친 미션만 일자 조건으로 먼저 거르고, 시·분·초 단위의 정확한 기간 판단은 참여 정책에서 합니다.
- 보상 아이템 조회는 무작위 선택에 순서가 필요 없어 정렬을 뺐습니다. 무작위 선택은 인덱스가 아니라 아이템 자체를 고르도록(`RewardRandomizer.pick`) 바꿨습니다.
- 직전 참여 조회는 PK 역순 조건을 뺐습니다. 같은 사용자·미션은 1시간 안에 다시 참여할 수 없어 일자·시간만으로 순서가 정해집니다.

## 6. 외부 쿠폰 시스템 재현

- `CouponClient` 인터페이스가 과제 8절 계약(8.1 템플릿 조회, 8.2 발급, 8.3 발급 결과 조회)을 그대로 표현합니다.
- `FakeCouponSystem`은 계약의 모든 결과를 재현합니다. 성공, 404, 한도 소진, 유효하지 않은 템플릿(미존재·발급 중지), 동일 requestId 멱등, 동일 requestId에 다른 내용이면 거부.
- 외부 시스템 한 대를 흉내 내므로 상태 변경 메서드를 `synchronized`로 원자 처리했습니다.
- 실제 연동할 때는 HTTP 구현체로 `CouponClient`만 교체하면 됩니다.

## 7. 중요 문제와 우선순위 (과제 12·14절)

> 이번 작업 범위(최초 프롬프트)에서는 과제 12·14절 "추가로 발견한 문제" 선정과 작성을 제외했습니다. 후속 작업에서 작성할 예정입니다.
>
> 참고로 2.3절의 동시성 제어와 쿠폰 멱등 처리는 과제 5절(반복·동시 요청)과 7절(참여 이력당 보상 1회) 정책을 지키기 위한 **기본 구현 범위**로 보고 구현했습니다.

## 8. 검증 방법

- JUnit5 자동화 테스트 91개 (단위 24 / 통합 37 / API 24 / 설정 6)
  - 단위: 참여 정책 경계값, KST 변환, 쿠폰 재현체 계약
  - 통합: 실제 H2·트랜잭션·락을 쓰는 미션/보상 시나리오, 시간 경과(MutableClock)
  - 동시성: 스레드 여러 개로 동시 요청 → 결과 건수 검증
  - API: MockMvc로 응답 포맷, HTTP 상태, 에러 코드, 요청값 검증(필수·형식·범위) 확인
- QA 항목과 테스트 매핑: `test-or-verification/QA_LIST.md`
- 실행 결과: `test-or-verification/TEST_RESULT.md`

## 9. 현재 구현의 한계와 추가 개선

- 같은 미션에 대한 완료 처리가 미션 행 락으로 직렬 처리되어, 참여가 몰리는 미션에서는 처리량이 제한됩니다.
- 외부 쿠폰 호출이 DB 트랜잭션 안에서 이뤄져, 외부 응답이 지연되면 락 점유 시간이 길어집니다. (개선 방향: 보상 상태를 PENDING으로 먼저 커밋한 뒤 트랜잭션 밖에서 발급하고 결과를 반영)
- 목록 조회는 미션마다 집계 쿼리를 실행합니다. 미션 수가 많아지면 한 번에 집계하는 쿼리로 바꿔야 합니다.
- 인증이 없어 `userId`만 알면 다른 사용자로 요청할 수 있습니다. (과제 11절 가정)
