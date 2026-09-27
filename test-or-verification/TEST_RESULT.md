# 테스트 실행 결과

## 0. 최신 실행 (교정 5 적용 후: 조회 캐싱 · 동시성 제어 성능 검증)

| 항목 | 값 |
|---|---|
| 실행일 | 2026-09-26 (KST) |
| 명령 | `gradlew.bat clean test` |
| 결과 | **92개 전체 통과** (기존 86 + 캐싱 3 + 락 성능 3) |

| 테스트 클래스 | QA | 테스트 수 | 실패 |
|---|---|---:|---:|
| `unit.KstTimeTest` | QA-T | 3 | 0 |
| `unit.ParticipationPolicyTest` | QA-P | 8 | 0 |
| `unit.FakeCouponSystemTest` | QA-C | 8 | 0 |
| `integration.MissionServiceTest` | QA-M | 12 | 0 |
| `integration.RewardServiceTest` | QA-R | 13 | 0 |
| `integration.CouponFailureTest` | QA-F | 6 | 0 |
| `integration.ConcurrencyTest` | QA-X | 4 | 0 |
| `integration.BusinessKeyTest` | QA-K | 6 | 0 |
| `integration.FailureScenarioTest` | QA-D | 2 | 0 |
| `integration.DatabaseFailureTest` | QA-D | 1 | 0 |
| `integration.MissionCacheTest` | QA-H | 3 | 0 |
| `integration.LockPerformanceTest` | QA-S | 3 | 0 |
| `api.RewardApiTest` | QA-A | 7 | 0 |
| `api.RequestValidationApiTest` | QA-V | 16 | 0 |
| **합계** | | **92** | **0** |

### 동시성 제어 락 성능 측정 결과 (`LockPerformanceTest` 로그 `[PERF]`)

측정 환경: 로컬 PC, H2 In-memory(`LOCK_TIMEOUT=10000`), HikariCP 커넥션 풀 기본 10개, 요청마다 스레드 1개로 동시에 출발. 수치는 실행마다 조금씩 달라집니다.

QA-S01 같은 미션에 동시 완료 요청 (요청 수별, 사용자는 모두 다름)

| 구분 | 요청 수 | 전체 처리 시간 | TPS | 성공 | 정책상 거절 | 락 대기 실패 | 기타 실패 | 평균 응답 | 최대 응답 |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|
| 완료 10건 | 10 | 59 ms | 169 | 10 | 0 | 0 | 0 | 35.4 ms | 59.2 ms |
| 완료 50건 | 50 | 168 ms | 298 | 50 | 0 | 0 | 0 | 86.5 ms | 168.2 ms |
| 완료 100건 | 100 | 283 ms | 353 | 100 | 0 | 0 | 0 | 138.2 ms | 280.0 ms |
| 완료 200건 | 200 | 377 ms | 531 | 100 | 100 (`MISSION_TOTAL_LIMIT_EXCEEDED`) | 0 | 0 | 196.2 ms | 372.5 ms |

QA-S02 참여 완료 → 보상 요청 동시 처리 (이력 100건마다 같은 보상 요청 2번)

| 구분 | 요청 수 | 전체 처리 시간 | TPS | 성공 | 정책상 거절 | 락 대기 실패 | 기타 실패 | 평균 응답 | 최대 응답 |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|
| 완료 100건 | 100 | 168 ms | 595 | 100 | 0 | 0 | 0 | 83.5 ms | 166.9 ms |
| 보상 200건 (이력당 2번) | 200 | 97 ms | 2062 | 100 | 100 (`REWARD_ALREADY_GRANTED`) | 0 | 0 | 44.2 ms | 94.7 ms |

QA-S03 락 경합 비교 (각 100건)

| 구분 | 요청 수 | 전체 처리 시간 | TPS | 성공 | 락 대기 실패 | 평균 응답 | 최대 응답 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 같은 미션 1개 | 100 | 145 ms | 690 | 100 | 0 | 71.5 ms | 143.5 ms |
| 서로 다른 미션 10개 × 10건 | 100 | 53 ms | 1887 | 100 | 0 | 25.6 ms | 44.2 ms |

해석

- 모든 단계에서 락 대기 실패 0건, 기타 실패 0건, 중복 참여·중복 보상 0건
- 같은 미션 요청은 미션 락으로 한 건씩 처리되어, 요청 수가 늘면 평균 응답 시간이 함께 늘어남 (10건 35ms → 200건 196ms)
- 같은 미션 100건(145ms)은 서로 다른 미션으로 나눈 경우(53ms)보다 약 2.7배 느림 → 병목은 같은 미션에 요청이 몰릴 때 생김
- 요청 1건의 락 점유는 약 1.5ms로, 락 대기 제한(10초)까지 여유가 커서 측정 범위(200건)에서는 병목이 실패로 이어지지 않음
- 보상 요청은 참여 이력 단위로 락을 걸어 서로 다른 이력끼리는 경합하지 않음 (200건 97ms)

교정 5 완료 조건 확인

| 완료 조건 | 결과 |
|---|---|
| 같은 일자 반복 조회 시 첫 요청만 DB 조회, 미션 변경 시 캐시 갱신 | QA-H01~H03 통과. 같은 일자 3번 조회 시 DB 쿼리 1 → 0 → 0회, 일자별 키 분리. 캐시 무효화 로직은 제거하여(추가 지시) 미션 변경은 TTL 10분 만료 후 반영, TTL 설정 확인(QA-H03) |
| 동시 요청에서 중복 참여·중복 보상 없이 정합성 유지 | QA-S01~S03 통과. 사용자별 참여 1건, 이력당 보상 1건, 발급 쿠폰 수 = 쿠폰 지급 보상 수 |
| 동시 요청 수에 따른 처리 시간·실패 건수로 락 병목 여부 확인 | 위 측정 결과. 10/50/100/200건 모두 락 대기 실패 0건. 같은 미션 집중 시 처리 시간이 약 2.7배 늘어나는 직렬화 병목은 있으나 실패로 이어지지 않음 |

---

## 이전 실행 기록 (교정 4)

### 교정 4 적용 후: 쿠폰 예외 보완 · 반복문 개선 · 실패 테스트

| 항목 | 값 |
|---|---|
| 실행일 | 2026-09-26 (KST) |
| 명령 | `gradlew.bat clean test bootJar` |
| 결과 | **87개 전체 통과** (기존 80 + 실패·장애 7: 쿠폰 호출 실패 QA-F03~F06, 애플리케이션·DB·기간 초과 QA-D01~D03) |

| 테스트 클래스 | QA | 테스트 수 | 실패 |
|---|---|---:|---:|
| `unit.KstTimeTest` | QA-T | 3 | 0 |
| `unit.ParticipationPolicyTest` | QA-P | 8 | 0 |
| `unit.FakeCouponSystemTest` | QA-C | 8 | 0 |
| `integration.MissionServiceTest` | QA-M | 12 | 0 |
| `integration.RewardServiceTest` | QA-R | 13 | 0 |
| `integration.CouponFailureTest` | QA-F | 6 | 0 |
| `integration.ConcurrencyTest` | QA-X | 4 | 0 |
| `integration.BusinessKeyTest` | QA-K | 6 | 0 |
| `integration.FailureScenarioTest` | QA-D | 2 | 0 |
| `integration.DatabaseFailureTest` | QA-D | 1 | 0 |
| `api.RewardApiTest` | QA-A | 8 | 0 |
| `api.RequestValidationApiTest` | QA-V | 16 | 0 |
| **합계** | | **87** | **0** |

교정 4 완료 조건 확인

| 완료 조건 | 결과 |
|---|---|
| 쿠폰 API IO 오류·타임아웃 → 보상 처리, 정의된 에러 코드·한글 메시지 | QA-F03~F06 통과. IO 오류·읽기 타임아웃은 503 `COUPON_COMMUNICATION_FAILED`, 기타 예외는 500 `COUPON_SYSTEM_ERROR`와 한글 메시지로 응답하고 보상 결과 `FAILED` 저장. 회복 후 재요청하면 같은 리워드번호로 지급. (재시도 로직은 두지 않았으므로 "정해진 횟수만큼 재시도"는 해당 없음 — 쿠폰 API는 1회 호출) |
| for 문 변경 후 기존 테스트 결과 동일 | 기존 80개 모두 통과 (쿠폰 제외 후 재선정 QA-F01, 한도 소진 QA-R09·R13, 동시 발급 QA-X04 포함). QA-F02만 실패 내역 저장에 맞춰 기대값 변경 (저장 안 됨 → FAILED 저장) |
| 추가 실패 테스트 4종 통과 | 애플리케이션 시스템 이슈(QA-D01), DB 시스템 이슈(QA-D03), 참여 기간 초과(QA-D02), 쿠폰 통신 오류·보상 처리(QA-F03~F06) 모두 통과 |

---

## 이전 실행 기록 (교정 3 조정)

### 교정 3 조정: 과도한 적용 제외

| 항목 | 값 |
|---|---|
| 실행일 | 2026-09-26 (KST) |
| 명령 | `gradlew.bat clean test bootJar` |
| 결과 | **80개 전체 통과** |

조정 내용

- 로그 출력 검증 테스트(`TraceIdLoggingTest`, 4건)와 실행 계획 테스트(`ExplainPlanTest`, 7건)를 자동화 테스트에서 제외. 로그는 실제 서버 실행으로, 인덱스 사용은 아래 H2 `EXPLAIN` 결과로 한 번 확인한 것으로 대신함
- `countByMissionId`, `findByParticipationNo`(참여 이력·보상 결과)는 JPQL 대신 JPA 메소드명 쿼리로 되돌림. 조회 조건은 같으므로 사용하는 인덱스도 같음

| 테스트 클래스 | QA | 테스트 수 | 실패 |
|---|---|---:|---:|
| `unit.KstTimeTest` | QA-T | 3 | 0 |
| `unit.ParticipationPolicyTest` | QA-P | 8 | 0 |
| `unit.FakeCouponSystemTest` | QA-C | 8 | 0 |
| `integration.MissionServiceTest` | QA-M | 12 | 0 |
| `integration.RewardServiceTest` | QA-R | 13 | 0 |
| `integration.CouponFailureTest` | QA-F | 2 | 0 |
| `integration.ConcurrencyTest` | QA-X | 4 | 0 |
| `integration.BusinessKeyTest` | QA-K | 6 | 0 |
| `api.RewardApiTest` | QA-A | 8 | 0 |
| `api.RequestValidationApiTest` | QA-V | 16 | 0 |
| **합계** | | **80** | **0** |

---

## 이전 실행 기록 (교정 3)

### 교정 3 적용 후: 비즈니스 키 · JPQL 전환 · 인덱스

| 항목 | 값 |
|---|---|
| 실행일 | 2026-09-26 (KST) |
| 명령 | `gradlew.bat clean test bootJar` |
| 결과 | **91개 전체 통과** (기존 77 + 비즈니스 키 6 + 실행 계획 7 + 검증 테스트 변경 1) |

| 테스트 클래스 | QA | 테스트 수 | 실패 |
|---|---|---:|---:|
| `unit.KstTimeTest` | QA-T | 3 | 0 |
| `unit.ParticipationPolicyTest` | QA-P | 8 | 0 |
| `unit.FakeCouponSystemTest` | QA-C | 8 | 0 |
| `integration.MissionServiceTest` | QA-M | 12 | 0 |
| `integration.RewardServiceTest` | QA-R | 13 | 0 |
| `integration.CouponFailureTest` | QA-F | 2 | 0 |
| `integration.ConcurrencyTest` | QA-X | 4 | 0 |
| `integration.BusinessKeyTest` | QA-K | 6 | 0 |
| `integration.ExplainPlanTest` | QA-I | 7 | 0 |
| `api.RewardApiTest` | QA-A | 8 | 0 |
| `api.RequestValidationApiTest` | QA-V | 16 | 0 |
| `api.TraceIdLoggingTest` | QA-L | 4 | 0 |
| **합계** | | **91** | **0** |

교정 3 완료 조건 확인

| 완료 조건 | 결과 |
|---|---|
| 동시 요청에서 이력번호·리워드번호 중복 없음, 유니크 제약 동작 | QA-K01~K06 통과. 채번 300건·미션 완료 100건·보상 50건 동시 실행에서 중복 0건. 같은 번호 저장 시 `DataIntegrityViolationException` |
| JPQL 전환 후 기존 테스트 통과 (조회 결과 동일) | 기존 미션·보상·동시성·API 테스트 모두 통과. API 식별자 변경(participationId → participationNo)에 맞춰 테스트 입력값만 수정 |
| EXPLAIN으로 설계 인덱스 사용, 불필요한 정렬 제거 확인 | QA-I01~I07 통과 (아래 실행 계획) |

실행 계획 요약 (H2 `EXPLAIN`, 사용 인덱스 주석 부분)

| 조회 | 실행 계획 |
|---|---|
| 미션 전체 참여 수 | `/* PUBLIC.IDX_PARTICIPATION_MISSION_USER_DATETIME: MISSION_ID = 'MISSION_0002' */` |
| 사용자 당일 참여 수 | `/* PUBLIC.IDX_PARTICIPATION_MISSION_USER_DATETIME: PARTICIPATED_DATE = '20260801' AND MISSION_ID = 'MISSION_0002' AND USER_ID = 'USER_0001' */` |
| 사용자 직전 참여 1건 | `/* PUBLIC.IDX_PARTICIPATION_MISSION_USER_DATETIME: MISSION_ID = 'MISSION_0002' AND USER_ID = 'USER_0001' */ ... ORDER BY 2 DESC, 1 DESC FETCH FIRST ROW ONLY` |
| 이력번호 단건 | `/* PUBLIC.UK_PARTICIPATION_NO_INDEX_7: PARTICIPATION_NO = '...' */` |
| 이력번호로 보상 결과 | `/* PUBLIC.UK_REWARD_PARTICIPATION_NO_INDEX_8: PARTICIPATION_NO = '...' */` |
| 보상 아이템 | `/* PUBLIC.IDX_MISSION_ITEM_MISSION: MISSION_ID = 'MISSION_0002' */` (ORDER BY 없음) |
| 참여 기간 미션 | `/* PUBLIC.IDX_MISSION_ENTRY_PERIOD: ENTRY_START_DATE <= '20260901' AND ENTRY_END_DATE >= '20260901' */ ... ORDER BY 5` |

- 직전 참여 조회는 인덱스로 (미션, 사용자) 범위를 찾은 뒤 H2가 그 범위 안에서 역순 정렬합니다. 같은 사용자·미션의 이력만 정렬하므로 비용이 작지만, H2 실행 계획에 "index sorted"(정렬 생략)는 나오지 않았습니다.
- 참여 기간 미션 조회의 `ORDER BY mission_id`는 응답 순서를 고정하려고 남겼습니다. 기간 조건으로 걸러진 소수의 행만 정렬합니다.

실제 서버(local) 확인: 미션 완료 응답 `participationNo=PT202609010000000001`, 보상 응답 `rewardNo=RW202609010000000001`, DB PK(`/reward/USER_0001/1`)로 요청하면 `PARTICIPATION_NOT_FOUND`

실행 중 발견한 사항

| 회차 | 결과 | 원인 | 조치 |
|---|---|---|---|
| 1회차 | 컴파일 실패 | `RewardService.getReward`의 `findByParticipationId` 호출 1곳이 바뀌지 않음 | `findByParticipationNo`로 수정 |
| 2회차 | 91/91 통과 | 실행 계획 확인 결과 참여 기간 미션 조회도 `idx_mission_entry_period`를 사용 | QA-I07에 인덱스 이름 확인 추가 |
| 3회차 | 91/91 통과 | - | - |

---

## 이전 실행 기록 (교정 2)

### 교정 2 적용 후: 환경 설정 분리 · logback · MDC

| 항목 | 값 |
|---|---|
| 실행일 | 2026-09-26 (KST) |
| 명령 | `gradlew.bat clean test bootJar` |
| 결과 | **77개 전체 통과** (기존 73 + 요청 추적 로그 4) |

| 테스트 클래스 | QA | 테스트 수 | 실패 |
|---|---|---:|---:|
| `unit.KstTimeTest` | QA-T | 3 | 0 |
| `unit.ParticipationPolicyTest` | QA-P | 8 | 0 |
| `unit.FakeCouponSystemTest` | QA-C | 8 | 0 |
| `integration.MissionServiceTest` | QA-M | 12 | 0 |
| `integration.RewardServiceTest` | QA-R | 13 | 0 |
| `integration.CouponFailureTest` | QA-F | 2 | 0 |
| `integration.ConcurrencyTest` | QA-X | 4 | 0 |
| `api.RewardApiTest` | QA-A | 8 | 0 |
| `api.RequestValidationApiTest` | QA-V | 15 | 0 |
| `api.TraceIdLoggingTest` | QA-L | 4 | 0 |
| **합계** | | **77** | **0** |

### 프로파일별 기동 확인 (`java -jar ... --spring.profiles.active=<프로파일>`)

같은 사용자로 `POST /linepay/v1/mission/USER_0001/MISSION_0003/complete`를 호출해 확인했습니다.

| 프로파일 | 활성 프로파일 로그 | 참여 일자 (기준 시각) | H2 콘솔 | 로그 출력 위치 | 같은 traceId 로그 |
|---|---|---|---|---|---|
| local | `The following 1 profile is active: "local"` | `20260901` (고정 시각 적용) | 200 | 콘솔 (파일 없음) | `[REQ]`·`[MISSION] completed`·`[RES]` 3줄 |
| dev | `... "dev"` | `20260926` (시스템 시각) | 200 | 콘솔 | 3줄 |
| prod | `... "prod"` (로그 파일에서 확인) | `20260926` (시스템 시각) | 404 (비활성) | 파일 `linepay-reward.log` (콘솔에는 기동 배너만) | 로그 파일에 3줄 |

교정 2 완료 조건 확인

| 완료 조건 | 결과 |
|---|---|
| 프로파일별 설정 적용, 운영 프로파일에 테스트용 설정 없음 | 위 표 참고. prod는 기준 시각이 고정되지 않고 H2 콘솔이 꺼짐 |
| 개발은 콘솔, 운영은 로그 파일 | local·dev는 콘솔에 앱 로그 출력, prod는 콘솔에 앱 로그가 없고 `linepay-reward.log`에 기록 |
| 같은 요청은 같은 traceId, 요청 간 MDC 섞이지 않음 | QA-L06~L08 통과. 실제 서버에서도 한 요청의 로그 3줄이 같은 traceId |

실행 중 발견한 사항

| 회차 | 결과 | 원인 | 조치 |
|---|---|---|---|
| 1회차 | 76/76 통과 | prod 기동 시 콘솔에 logback 경고 `Appender named [CONSOLE] not referenced` 출력 | 콘솔 appender 정의를 `!prod` 프로파일 블록 안으로 이동 |
| 2회차 | 76/76 통과 | prod 콘솔 경고 없음 확인 | - |
| 3회차 | 77/77 통과 | 운영 로그 강화: DB 조회·주요 로직 단계별 한글 로그 추가, 로그 문구 변경에 맞춰 QA-L06~L08 기대값 수정, QA-L09 추가 | 실제 서버(local)에서 미션 완료·보상 지급 요청 로그 확인 (아래 예시) |

운영 로그 강화 후 실제 서버 로그 (local, traceId 일부 생략)

```
INFO  [d6b6...] [REQ] 요청 시작 - POST /linepay/v1/mission/USER_0002/MISSION_0002/complete
INFO  [d6b6...] [MISSION] 미션 완료 처리 시작 userId=USER_0002 missionId=MISSION_0002
INFO  [d6b6...] [USER] 사용자 조회 userId=USER_0002 존재여부=true
INFO  [d6b6...] [MISSION] 미션 락 획득 missionId=MISSION_0002 참여기간=2026-05-01T00:00~2027-01-01T00:00
INFO  [d6b6...] [MISSION] 참여 조건 검사 missionId=MISSION_0002 userId=USER_0002 전체참여=0 당일참여=0 직전참여=null 결과=참여가능
INFO  [d6b6...] [MISSION] 참여 이력 저장 완료 userId=USER_0002 missionId=MISSION_0002 participationId=1 참여일시=2026-09-01T12:00
INFO  [d6b6...] [RES] 요청 종료 - POST /linepay/v1/mission/USER_0002/MISSION_0002/complete 상태=200 처리시간=64ms

INFO  [99a1...] [REQ] 요청 시작 - POST /linepay/v1/reward/USER_0002/1
INFO  [99a1...] [REWARD] 보상 지급 요청 시작 userId=USER_0002 participationId=1
INFO  [99a1...] [USER] 사용자 조회 userId=USER_0002 존재여부=true
INFO  [99a1...] [REWARD] 참여 이력 락 획득 participationId=1 missionId=MISSION_0002
INFO  [99a1...] [REWARD] 기존 보상 결과 조회 participationId=1 상태=없음
INFO  [99a1...] [REWARD] 보상 아이템 조회 missionId=MISSION_0002 건수=2 아이템=[ITEM_0002(REWARD_POINT), ITEM_0003(COUPON)]
INFO  [99a1...] [COUPON] 기존 발급 결과 조회 requestId=REWARD_1_COUPON_TEMPLATE_0001 결과=없음
INFO  [99a1...] [COUPON] 쿠폰 템플릿 조회 couponTemplateId=COUPON_TEMPLATE_0001 상태=AVAILABLE 발급수량=0/100 발급가능=true
INFO  [99a1...] [REWARD] 지급 후보 확정 건수=2 후보=[ITEM_0002, ITEM_0003]
INFO  [99a1...] [REWARD] 보상 아이템 무작위 선택 아이템=ITEM_0002 유형=REWARD_POINT (후보 2건 중)
INFO  [99a1...] [REWARD] 포인트 지급 결정 포인트=5
INFO  [99a1...] [REWARD] 보상 결과 저장 완료 participationId=1 상태=GRANTED 아이템=ITEM_0002 유형=REWARD_POINT 포인트=5 couponId=null
INFO  [99a1...] [RES] 요청 종료 - POST /linepay/v1/reward/USER_0002/1 상태=200 처리시간=33ms
```

---

## 이전 실행 기록 (교정 1)

### 교정 1 적용 후: Lombok · 생성자 주입 · Spring Validation

| 항목 | 값 |
|---|---|
| 실행일 | 2026-09-26 (KST) |
| 명령 | `gradlew.bat clean test bootJar` |
| 결과 | **73개 전체 통과** (기존 58 + 요청값 검증 15) |

| 테스트 클래스 | QA | 테스트 수 | 실패 |
|---|---|---:|---:|
| `unit.KstTimeTest` | QA-T | 3 | 0 |
| `unit.ParticipationPolicyTest` | QA-P | 8 | 0 |
| `unit.FakeCouponSystemTest` | QA-C | 8 | 0 |
| `integration.MissionServiceTest` | QA-M | 12 | 0 |
| `integration.RewardServiceTest` | QA-R | 13 | 0 |
| `integration.CouponFailureTest` | QA-F | 2 | 0 |
| `integration.ConcurrencyTest` | QA-X | 4 | 0 |
| `api.RewardApiTest` | QA-A | 8 | 0 |
| `api.RequestValidationApiTest` | QA-V | 15 | 0 |
| **합계** | | **73** | **0** |

교정 1 완료 조건 확인

| 완료 조건 | 결과 |
|---|---|
| 기존 테스트 통과 | 기존 58개 모두 통과. 에러 응답 형식 변경(`code`=영문 사유 코드, `msg`=한글)에 맞춰 `RewardApiTest`의 기대값만 수정 |
| 필수값 누락·형식 오류·범위 오류 → 400 + 정의된 코드·메시지 | QA-V01~V09 통과. 실제 서버에서도 `MISSING_REQUIRED_VALUE` / `INVALID_FORMAT` / `OUT_OF_RANGE` 응답과 한글 메시지 확인 |
| 애플리케이션 기동 시 빈 주입 오류 없음 | `java -jar`로 기동: `Started LinepayRewardApplication in 5.9 seconds`, 빈 생성·주입 오류 로그 없음 |

실행 중 발견한 사항

| 회차 | 결과 | 원인 | 조치 |
|---|---|---|---|
| 1회차 | 72/73 통과 | QA-V03의 `USER_0001;DROP`이 200으로 통과. Spring MVC가 `;` 뒤를 matrix variable로 보고 잘라내 `USER_0001`로 검증함 (검증 누락이 아니라 프레임워크 동작) | 테스트 값을 `USER@0001`, `USER.0001`로 바꾸고 이 동작을 api-spec.md 1.1에 기록 |
| 2회차 | 73/73 통과 | - | - |
| 3회차 | 66/73 통과 (QA-V03·V04·V05 7건 실패) | 컨트롤러에서 `userId`·`missionId`의 `@Size`·`@Pattern`을 빼고 `RequestIdRule`을 삭제하는 코드 변경이 있었음. 테스트는 이전 규칙(400 INVALID_FORMAT)을 기대하고 있어, 검증을 통과한 요청이 서비스에서 404로 거절되며 실패함 | 바뀐 규칙(필수값만 검증)에 맞춰 QA-V03~V05를 "검증 통과 → 404 USER_NOT_FOUND / MISSION_NOT_FOUND"로 수정 |
| 4회차 | 73/73 통과 (전체 실행, 단독 실행 모두) | - | - |
| 5회차 | 73/73 통과 | `HandlerMethodValidationException` 처리 단순화(우선순위 비교·ConstraintViolation 변환 제거) 후 재실행 | 검증 응답 코드·메시지 변화 없음 |

---

## 이전 실행 기록 (초기 구현)

## 1. 실행 환경

| 항목 | 값 |
|---|---|
| 실행일 | 2026-09-25 (KST) |
| OS | Windows (IntelliJ 통합 터미널) |
| JDK | Java 21.0.10 |
| Gradle | 8.14.3 (Wrapper) |
| 명령 | `gradlew.bat clean test` |

## 2. 자동화 테스트 결과: 58개 전체 통과

| 테스트 클래스 | QA | 테스트 수 | 실패 | 소요(s) |
|---|---|---:|---:|---:|
| `unit.KstTimeTest` | QA-T | 3 | 0 | 0.008 |
| `unit.ParticipationPolicyTest` | QA-P | 8 | 0 | 0.005 |
| `unit.FakeCouponSystemTest` | QA-C | 8 | 0 | 0.008 |
| `integration.MissionServiceTest` | QA-M | 12 | 0 | 0.599 |
| `integration.RewardServiceTest` | QA-R | 13 | 0 | 0.296 |
| `integration.CouponFailureTest` | QA-F | 2 | 0 | 0.162 |
| `integration.ConcurrencyTest` | QA-X | 4 | 0 | 0.959 |
| `api.RewardApiTest` | QA-A | 8 | 0 | 1.022 |
| **합계** | | **58** | **0** | |

```
BUILD SUCCESSFUL
TOTAL tests=58 failed=0
```

- 동시성 테스트(QA-X)가 우연히 통과한 것이 아닌지 확인하려고 `--rerun-tasks`로 3회 더 실행했고, 모두 통과했습니다.
- 상세 리포트는 `gradlew test` 실행 후 `build/reports/tests/test/index.html`에서 볼 수 있습니다.

### 실행 중 발견·수정한 사항

| 회차 | 결과 | 원인 | 조치 |
|---|---|---|---|
| 1회차 | 57/58 통과 | QA-A03에서 `jsonPath("$.data").exists()`는 값이 `null`인 필드를 "없음"으로 판단함 (기능 결함 아님) | 응답 JSON을 직접 파싱해 `data` 키가 있고 값이 `null`인지 확인하도록 테스트 수정 |
| 2회차 이후 | 58/58 통과 | - | - |

## 3. 실제 서버 기동 후 API 확인 (Smoke Test)

`bootJar`로 만든 jar를 실행하고(기준 시각 2026-09-01T12:00:00+09:00) 실제 HTTP로 호출한 결과입니다.

| 요청 | HTTP | 결과 |
|---|---|---|
| `GET /linepay/v1/mission/USER_0001` | 200 | MISSION_0002, MISSION_0003 반환, `Content-Type: application/json;charset=UTF-8` |
| `POST /linepay/v1/mission/USER_0001/MISSION_0003/complete` | 200 | participationId=1, `20260901` / `120000` |
| 같은 요청 반복 | 409 | `E409 MISSION_REENTRY_COOLDOWN`, `data: null` |
| `POST /linepay/v1/reward/USER_0001/1` | 200 | GRANTED, REWARD_POINT, pointAmount=7 |
| 같은 요청 반복 | 409 | `E409 REWARD_ALREADY_GRANTED` |
| `POST /linepay/v1/mission/USER_0002/MISSION_0002/complete` | 200 | participationId=2 |
| `POST /linepay/v1/reward/USER_0002/2` | 200 | GRANTED, ITEM_0002(REWARD_POINT), pointAmount=8 |
| `GET /linepay/v1/reward/USER_0002/2` | 200 | 지급 결과와 같음 |
| `GET /linepay/v1/mission/USER_9999` | 404 | `E404 USER_NOT_FOUND` |
| `GET /linepay/v1/reward/USER_0001/abc` | 400 | `E400 INVALID_REQUEST` |

- 응답의 한글 미션 제목이 UTF-8로 올바르게 내려오는지 코드 포인트로 확인했습니다. (예: "랜덤 박스 열고 포인트 받기")
- 같은 시나리오는 `api-verification.http`(IntelliJ HTTP Client)로 다시 실행할 수 있습니다.

## 4. 검증하지 못한 범위

`QA_LIST.md` 9절을 참고해 주세요.
