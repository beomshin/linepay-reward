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
| `common.config` | 서비스 기준 시각 `Clock` 빈 (고정 시각 또는 시스템 시각), 캐시 설정 `CacheConfig` (교정 5) |
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

## 6. 예외 처리 및 장애 방지 (교정 4)

**쿠폰 API 호출부 예외 처리 (동기 호출)**

`getCouponTemplate`, `issueCoupon`, `getCouponIssue`를 호출하는 3곳(`isCouponIssuable`, 발급 반복문, `findIssuedCoupon`)에서 예외를 종류별로 직접 처리합니다.

| 예외 | 처리 | 응답 |
|---|---|---|
| `CouponApiException` 중 업무 결과 (한도 소진, 템플릿 없음, 발급 이력 없음 등) | 기존대로 결과별 처리 (후보 제외, NO_REWARD 등) | 정상 흐름 |
| `CouponApiException` 중 예상하지 못한 응답 (REQUEST_ID_CONFLICT 등) | `CouponFailureException` → 실패 내역 저장 | 500 `COUPON_SYSTEM_ERROR` |
| `UncheckedIOException` (IO 오류, 읽기 타임아웃 `SocketTimeoutException` 등) | `CouponFailureException` → 실패 내역 저장 | 503 `COUPON_COMMUNICATION_FAILED` |
| 그 외 `Exception` | `CouponFailureException` → 실패 내역 저장 | 500 `COUPON_SYSTEM_ERROR` |

- **실패 내역 저장:** `requestReward`가 `CouponFailureException`을 받아 보상 결과를 `FAILED`(실패 사유 예: `쿠폰 발급 실패 (UncheckedIOException)`)로 저장하고 `RewardFailedException`을 던집니다. `noRollbackFor = RewardFailedException`이라 실패 내역이 커밋됩니다.
- **재요청:** 재시도 로직은 두지 않고, 클라이언트가 같은 이력번호로 다시 요청합니다. 리워드번호는 유지되고, 이전 요청에서 실제로 발급된 쿠폰은 복구 단계에서 찾아 반영하며, requestId 멱등 키로 중복 발급되지 않습니다.
- **타임아웃:** 실제 연동 시 HTTP 클라이언트의 연결·읽기 타임아웃으로 설정하고, 그때 나는 예외를 IO 오류로 처리합니다. (이번 과제는 재현체를 쓰므로 클라이언트 타임아웃 설정은 없음)
- 쿠폰 발급 성공 후의 보상 반영(`grantCoupon`)은 try 블록 밖에 두어, 쿠폰 API 호출 실패와 내부 처리 오류가 섞이지 않게 했습니다.

**반복문 개선:** 보상 후보 선정 `while (!candidates.isEmpty())`를 `for (pickCount < 최초 후보 수)`로 바꿨습니다. 매 반복마다 후보를 하나 고르고, 쿠폰 발급이 거절되면 그 후보를 빼므로 반복 횟수는 최초 후보 수를 넘지 않습니다.

**DB 장애:** `DataAccessException`(연결 실패, 락 대기 초과 등)을 전역 예외 핸들러에서 503 `DATABASE_ERROR`로 응답합니다.

## 7. 대용량 트래픽 대응 (교정 5)

**일자별 미션 조회 캐싱**

참여 가능 미션 조회는 호출 빈도가 높고, 그 안의 일자별 미션 조회(`findEntryPeriodMissions`)는 같은 날에는 결과가 같습니다. 이 조회를 조회 일자로 캐싱했습니다.

| 항목 | 내용 |
|---|---|
| 적용 위치 | `MissionRepository.findEntryPeriodMissions`에 `@Cacheable` (캐시 이름 `entryPeriodMissions`, 키 = 조회 일자 `yyyyMMdd`) |
| 캐시 구현 | Spring Cache + Caffeine 로컬 캐시 (과제 실행 조건상 Redis 같은 외부 인프라를 쓰지 않음) |
| 만료 시간(TTL) | 저장 후 10분 (`application.yml`의 `spring.cache.caffeine.spec: maximumSize=100,expireAfterWrite=10m`) |
| 무효화 | 별도 무효화 처리 없음. 미션 데이터 변경은 만료 시간(10분)이 지나면 반영 |

- 캐시에는 일자 조건까지만 담습니다. 시·분·초 단위의 기간 판단과 참여 횟수·재참여 검사는 캐시 밖(참여 정책)에서 매번 하므로, 일자 안에서 시각이 지나도 결과가 틀리지 않습니다.
- 사용자별 참여 횟수 조회는 요청마다 바뀌는 값이라 캐싱하지 않았습니다.
- 미션은 Seed Data로 적재되고 변경 API가 없어, 변경 시 무효화 로직은 두지 않았습니다. 미션 데이터를 바꾸면(DB 직접 수정 등) 최대 10분 뒤 조회 결과에 반영됩니다.
- 테스트에서는 테스트끼리 영향을 주지 않도록 매 테스트 전에 캐시를 비웁니다. (`IntegrationTestSupport`)

**동시성 제어 락 성능 검증**

`LockPerformanceTest`에서 여러 스레드가 동시에 같은 미션에 완료·보상 요청을 보내 처리 시간, 처리 건수, 락 대기 실패 건수를 측정했습니다. (H2 In-memory, 커넥션 풀 기본 10개, 측정값은 `TEST_RESULT.md`)

- 같은 미션 동시 완료 10/50/100/200건: 락 대기 실패 0건, 중복 참여 0건. 200건은 100건 성공, 100건은 전체 참여 한도로 거절
- 같은 미션 100건(145ms)과 서로 다른 미션 10개 × 10건(53ms) 비교: 미션 락으로 같은 미션 요청이 직렬 처리되어 약 2.7배 느림 → 병목은 "같은 미션에 몰릴 때"에 한정됨
- 요청 1건의 락 점유는 약 1.5ms로, 락 대기 시간 제한(`LOCK_TIMEOUT` 10초)에 비해 여유가 커서 측정 범위에서는 병목이 실패로 이어지지 않음
- 시간 수치는 실행 환경에 따라 달라지므로 테스트에서 검증(assert)하지 않고, 정합성(중복 참여·중복 보상 없음)과 실패 건수만 검증

## 8. 외부 쿠폰 시스템 재현

- `CouponClient` 인터페이스가 과제 8절 계약(8.1 템플릿 조회, 8.2 발급, 8.3 발급 결과 조회)을 그대로 표현합니다.
- `FakeCouponSystem`은 계약의 모든 결과를 재현합니다. 성공, 404, 한도 소진, 유효하지 않은 템플릿(미존재·발급 중지), 동일 requestId 멱등, 동일 requestId에 다른 내용이면 거부.
- 외부 시스템 한 대를 흉내 내므로 상태 변경 메서드를 `synchronized`로 원자 처리했습니다.
- 실제 연동할 때는 HTTP 구현체로 `CouponClient`만 교체하면 됩니다.

## 9. 추가로 발견한 문제와 구현 범위 (과제 12·14절)

> 2.3절의 동시성 제어와 쿠폰 멱등 처리는 과제 5절(반복·동시 요청)과 7절(참여 이력당 보상 1회) 정책을 지키기 위한 **기본 구현 범위**로 보고 구현했습니다. 이 절은 기본 구현 이후 추가로 발견한 문제를 다룹니다.

### 9.1 중요하다고 판단한 문제

**실제 운영 과정에서 발생할 수 있는 장애 포인트**

초기 구현은 과제 요구사항대로 동작했지만, 운영 환경을 가정하고 다시 살펴보니 다음과 같은 장애 포인트가 있었습니다.

| 구분 | 장애 포인트 | 운영 중 발생할 수 있는 상황 |
|---|---|---|
| 외부 연동 | 쿠폰 발급 시 `CouponApiException` 외의 예외(IO 오류, 타임아웃 등)가 처리되지 않음 | 쿠폰 시스템 통신 장애가 그대로 서비스 장애로 번지고, 보상 처리 결과가 남지 않음 |
| 로직 | 보상 후보를 `while` 문으로 선정 | 이후 조건이 바뀌면 무한루프에 빠져 요청 스레드가 고갈될 수 있음 |
| 데이터 | PK에 의존하는 식별 방식, 인덱스 없는 집계·전체 조회, 불필요한 정렬 | 운영 식별번호(이력번호·리워드번호)가 없고, 데이터가 늘수록 응답이 느려짐 |
| 트래픽 | 조회 빈도가 높은 미션 조회를 요청마다 DB에서 조회, 미션 락의 병목 여부 미검증 | 이벤트 등으로 트래픽이 몰리면 DB 부하와 락 대기가 늘어남 |
| 운영 추적 | 환경별 설정·로그 분리 없음, 요청 단위 추적(MDC) 없음 | 테스트용 기준 시각이 운영에 적용될 위험이 있고, 장애가 나도 원인 요청을 찾기 어려움 |

### 9.2 해당 문제를 중요하게 판단한 이유

- 이 서비스는 결제 서비스의 리워드 기능이므로, 고객에게 **신뢰할 수 있는 서비스**를 제공하는 것이 가장 중요하다고 판단했습니다.
- 포인트와 쿠폰은 고객에게 금전적 가치가 있습니다. 중복 지급은 회사의 손실이 되고, 미지급이나 처리 결과 누락은 고객 불만과 신뢰 하락으로 바로 이어집니다.
- 기능이 정상 상황에서 동작하는 것만으로는 부족하고, **외부 장애·트래픽 증가·데이터 증가 같은 비정상 상황에서도** 정합성을 지키고, 실패하더라도 결과를 남겨 복구하고 원인을 추적할 수 있어야 한다고 보았습니다.

### 9.3 실제로 해결하기로 선택한 범위

적절한 **인덱싱, 캐싱, 예외 처리**를 중심으로, 운영 중 장애를 미리 막고 발생한 장애를 추적할 수 있는 범위까지 구현했습니다.

| 영역 | 적용 내용 | 상세 |
|---|---|---|
| 예외 처리 | 쿠폰 API 예외를 종류별로 처리하고 실패 내역(`FAILED`)을 저장, DB 장애 시 503 `DATABASE_ERROR` 응답 | 6절 (교정 4) |
| 장애 방지 | 보상 후보 선정 `while` 문을 반복 횟수가 정해진 `for` 문으로 변경 | 6절 (교정 4) |
| 인덱싱·조회 | 비즈니스 키(이력번호·리워드번호) 도입, 조회별 인덱스 설계, 과도한 JPA 메소드명의 JPQL 전환, 불필요한 정렬 제거 | 5절 (교정 3) |
| 캐싱 | 일자별 미션 조회(`findEntryPeriodMissions`)를 조회 일자 키로 캐싱 | 7절 (교정 5) |
| 동시성 검증 | 동시 요청 수별 처리 시간·실패 건수 측정으로 락 병목 범위 확인 | 7절 (교정 5) |
| 운영 추적 | 환경별 설정 분리, 환경별 로그 출력, traceId 기반 요청 추적 | 4절 (교정 2) |
| 실패 테스트 | 애플리케이션·DB 장애, 참여 기간 초과, 쿠폰 통신 오류 실패 테스트 추가 | 10절 |

**이번 범위에서 제외한 것**

- 쿠폰 발급 자동 재시도, 트랜잭션 밖 비동기 발급: 클라이언트 재요청과 멱등 키로 중복 없이 복구할 수 있어 제외했습니다. (개선 방향은 11절)
- Redis 같은 외부 캐시·분산 락: 과제 실행 조건(외부 인프라 없음)에 맞지 않아 제외했습니다.

### 9.4 선택한 방법의 한계

현재 구현은 **단일 애플리케이션이 하나의 데이터베이스를 사용하는 구조**를 전제로 프로세스를 정의했습니다. 실제 운영 환경의 구성에 따라 다음과 같은 한계가 있습니다.

| 환경 | 현재 방식 | 한계 | 대응 방향 |
|---|---|---|---|
| 다중 데이터베이스 (샤딩, 읽기/쓰기 분리) | DB 시퀀스로 이력번호·리워드번호 채번 | DB마다 시퀀스가 따로 있어 번호가 중복될 수 있음 | 전역 ID 생성 방식(채번 전용 서버, Snowflake 방식 등) 도입 |
| | DB 행 락(`PESSIMISTIC_WRITE`)으로 동시성 제어 | 데이터가 여러 DB에 나뉘면 하나의 락으로 보호할 수 없음, 읽기 전용 DB는 복제 지연이 있음 | 분산 락, 정합성이 필요한 조회는 쓰기 DB에서 조회 |
| MSA (미션·보상·쿠폰 서비스 분리) | 참여 확인과 보상 저장을 하나의 DB 트랜잭션으로 처리 | 서비스가 나뉘면 하나의 트랜잭션으로 묶을 수 없음 | Saga 패턴, Outbox 패턴으로 결과 일관성 보장, 실패 시 보상 트랜잭션 |
| | traceId를 애플리케이션 내부 MDC로만 관리 | 서비스 사이를 넘나드는 요청은 하나로 추적되지 않음 | 서비스 간 헤더 전파와 분산 추적 도구 도입 |
| 다중 인스턴스 | Caffeine 로컬 캐시 | 인스턴스마다 캐시가 달라, 미션이 바뀌면 인스턴스별로 응답이 다를 수 있음 | Redis 같은 공유 캐시 또는 변경 이벤트 전파 |

- 즉, 이번 구현은 "단일 애플리케이션 + 단일 DB" 안에서 장애 포인트를 줄이는 데 집중했으며, 실제 운영 구조에 맞춰 채번·락·트랜잭션·캐시 방식을 다시 설계해야 합니다.
- 그 밖의 현재 구현 한계는 11절을 참고해 주세요.

## 10. 검증 방법

- JUnit5 자동화 테스트 104개 (단위 24 / 통합 50 / API 24 / 설정 6)
  - 단위: 참여 정책 경계값, KST 변환, 쿠폰 재현체 계약
  - 통합: 실제 H2·트랜잭션·락을 쓰는 미션/보상 시나리오, 시간 경과(MutableClock)
  - 동시성: 스레드 여러 개로 동시 요청 → 결과 건수 검증
  - 캐싱: Hibernate 통계의 쿼리 실행 횟수로 캐시 적중 시 DB 조회가 없는지 확인 (교정 5)
  - 락 성능: 동시 요청 수별 처리 시간·건수·락 대기 실패 측정 (교정 5)
  - API: MockMvc로 응답 포맷, HTTP 상태, 에러 코드, 요청값 검증(필수·형식·범위) 확인
- QA 항목과 테스트 매핑: `test-or-verification/QA_LIST.md`
- 실행 결과: `test-or-verification/TEST_RESULT.md`

## 11. 현재 구현의 한계와 추가 개선

- 같은 미션에 대한 완료 처리가 미션 행 락으로 직렬 처리되어, 참여가 몰리는 미션에서는 처리량이 제한됩니다. (측정 범위 200건까지는 락 대기 실패 없음. 7절 참고)
- 미션 캐시는 변경 시 무효화하지 않아, 미션 데이터 변경이 최대 TTL(10분)만큼 늦게 반영됩니다. 미션 변경 기능이 생기고 즉시 반영이 필요하면 변경 시 캐시 무효화(여러 인스턴스라면 공유 캐시나 변경 이벤트 전파)를 추가해야 합니다.
- 외부 쿠폰 호출이 DB 트랜잭션 안에서 이뤄져, 외부 응답이 지연되면 락 점유 시간이 길어집니다. 실제 연동 시 HTTP 클라이언트 타임아웃을 짧게 잡아 락 점유 시간을 제한해야 합니다. (개선 방향: 보상 상태를 PENDING으로 먼저 커밋한 뒤 트랜잭션 밖에서 발급하고 결과를 반영)
- 목록 조회는 미션마다 집계 쿼리를 실행합니다. 미션 수가 많아지면 한 번에 집계하는 쿼리로 바꿔야 합니다.
- 인증이 없어 `userId`만 알면 다른 사용자로 요청할 수 있습니다. (과제 11절 가정)
