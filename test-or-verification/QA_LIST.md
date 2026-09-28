# QA 리스트

생성한 로직을 기준으로 검증할 항목을 정리하고, 각 항목을 JUnit5 테스트와 연결했습니다.
테스트 이름(`@DisplayName`)에 QA ID가 들어 있어 리포트에서 바로 찾을 수 있습니다.

| 구분 | 테스트 클래스 | 종류 |
|---|---|---|
| QA-T | `unit/KstTimeTest` | 단위 |
| QA-P | `unit/ParticipationPolicyTest` | 단위 |
| QA-C | `unit/FakeCouponSystemTest` | 단위 |
| QA-M | `integration/MissionServiceTest` | 통합 (H2, 트랜잭션) |
| QA-R | `integration/RewardServiceTest` | 통합 |
| QA-F | `integration/CouponFailureTest` | 통합 + API (쿠폰 시스템 호출 실패, 외부 오류 주입) |
| QA-X | `integration/ConcurrencyTest` | 통합 (동시성) |
| QA-A | `api/RewardApiTest` | API (MockMvc) |
| QA-V | `api/RequestValidationApiTest` | API (MockMvc, 요청값 검증) |
| QA-K | `integration/BusinessKeyTest` | 통합 (비즈니스 키 채번, 유니크 제약) |
| QA-D | `integration/FailureScenarioTest`, `integration/DatabaseFailureTest` | 통합 + API (애플리케이션·DB 장애) |
| QA-H | `integration/MissionCacheTest` | 통합 (일자별 미션 조회 캐싱) |
| QA-S | `integration/LockPerformanceTest` | 통합 (동시성 제어 락 성능 측정) |

## 1. 시간 기준

| ID | 검증 항목 | 중요한 이유 |
|---|---|---|
| QA-T01 | UTC 15:00 → KST 다음 날 00:00으로 변환 | 일별 횟수가 KST 기준이어야 함 |
| QA-T02 | 일자 `yyyyMMdd`, 시간 `HHmmss` 상호 변환 | 저장 형식 요구사항 |
| QA-T03 | 나노초 절삭 (초 단위) | 저장 정밀도와 비교 정밀도를 맞춤 |

## 2. 참여 조건 경계값

| ID | 검증 항목 |
|---|---|
| QA-P01 | 모든 조건 충족 시 참여 가능 |
| QA-P02 | 기간 시작 시각과 같으면 가능 (`start <= now`) |
| QA-P03 | 시작 1초 전이면 불가 |
| QA-P04 | 종료 시각과 같으면 불가, 1초 전이면 가능 (`now < end`) |
| QA-P05 | 전체 99회 가능 / 100회 불가 |
| QA-P06 | 당일 9회 가능 / 10회 불가 |
| QA-P07 | 직전 참여 후 59분 59초 불가 / 정확히 1시간 가능 |
| QA-P08 | 여러 조건 위반 시 사유 우선순위 |

## 3. 외부 쿠폰 시스템 계약

| ID | 검증 항목 |
|---|---|
| QA-C01 | Seed 템플릿 조회 결과 (100개, 0개 발급, AVAILABLE) |
| QA-C02 | 없는 템플릿 조회 → 404 |
| QA-C03 | 발급 성공 시 발급 수량 +1 |
| QA-C04 | 같은 requestId·같은 내용 → 기존 결과 반환, 수량 그대로 |
| QA-C05 | 같은 requestId·다른 내용 → 거부 |
| QA-C06 | 한도 도달 → EXHAUSTED, 이후 발급은 한도 소진 결과 |
| QA-C07 | INACTIVE·없는 템플릿 발급 → 유효하지 않은 템플릿 결과 |
| QA-C08 | 발급 결과 조회 / 없으면 404 |

## 4. 미션 조회·완료

| ID | 검증 항목 |
|---|---|
| QA-M01 | 기준 시각(2026-09-01 12:00 KST)에는 MISSION_0002, 0003만 조회 (0001은 기간 종료) |
| QA-M02 | 없는 사용자 → USER_NOT_FOUND |
| QA-M03 | 완료 시 참여 이력 생성, 일자 `20260901` / 시간 `120000` |
| QA-M04 | 참여 이력마다 다른 ID로 구분 |
| QA-M05 | 기간 외 미션 → MISSION_NOT_IN_PERIOD, 이력 없음 |
| QA-M06 | 없는 미션 → MISSION_NOT_FOUND |
| QA-M07 | 참여 직후 목록에서 빠지고, 1시간 뒤 다시 조회됨 (다른 사용자는 영향 없음) |
| QA-M08 | 하루 10회 초과 불가, 23:59:59 불가 → 다음 날 00:00:00 가능 |
| QA-M09 | 1시간 제한은 자정을 넘겨도 유지 |
| QA-M10 | 전체 100회 도달 → 모든 사용자 참여 불가, 목록에서 빠짐 |
| QA-M11 | 거절된 요청은 참여 횟수에 들어가지 않음 |
| QA-M12 | 기간 시작 전 미션은 목록에 없고, 시작 시각부터 조회 |
| QA-M13 | 참여 기간 초과: 종료 시각(2027-01-01 00:00:00) 완료 요청 → MISSION_NOT_IN_PERIOD, 이력 없음. 1초 전(23:59:59)은 성공 |

## 5. 보상 지급·조회

| ID | 검증 항목 |
|---|---|
| QA-R01 | 포인트 전용 미션 → GRANTED, REWARD_POINT, 5~10 |
| QA-R02 | 포인트 값 5~10 전 범위가 나오고 범위를 벗어나지 않음 (500회) |
| QA-R03 | 같은 참여 이력 재요청 → REWARD_ALREADY_GRANTED, 기존 결과 그대로 |
| QA-R04 | 요청 전 결과 조회 → REWARD_NOT_FOUND |
| QA-R05 | 다른 사용자 / 없는 참여 이력 → PARTICIPATION_NOT_FOUND, 없는 사용자 → USER_NOT_FOUND |
| QA-R06 | 쿠폰 선택 → 외부 발급 1개, couponId 기록 |
| QA-R07 | MISSION_0002에서 포인트와 쿠폰이 모두 선택될 수 있음 |
| QA-R08 | 발급 중지 쿠폰은 후보에서 제외 |
| QA-R09 | 한도 소진 쿠폰은 후보에서 제외 |
| QA-R10 | 지급 가능 보상 없음 → NO_REWARD, 이후 재요청하면 지급 (결과 행 1개 유지) |
| QA-R11 | 외부에 없는 템플릿만 있으면 NO_REWARD |
| QA-R12 | 외부 발급은 됐지만 저장 전에 실패한 경우 재요청하면 같은 쿠폰으로 복구 (중복 발급 없음) |
| QA-R13 | 한도 1개 쿠폰: 첫 번째만 쿠폰, 두 번째는 NO_REWARD |

## 6. 쿠폰 시스템 호출 실패

| ID | 검증 항목 |
|---|---|
| QA-F01 | 조회 때는 가능했지만 발급 때 한도 소진 → 쿠폰 제외 후 포인트로 재선정 |
| QA-F02 | 예상하지 못한 외부 오류(REQUEST_ID_CONFLICT) → COUPON_SYSTEM_ERROR(500), 보상 결과 FAILED 저장 |
| QA-F03 | 쿠폰 발급 IO 오류 → 503 COUPON_COMMUNICATION_FAILED(한글 메시지), 보상 결과 FAILED 저장(롤백 안 됨), 결과 조회도 FAILED |
| QA-F04 | 쿠폰 발급 읽기 타임아웃(`SocketTimeoutException`) → FAILED, 쿠폰 시스템 회복 뒤 같은 이력번호로 재요청하면 같은 리워드번호로 쿠폰 지급, 쿠폰 1개만 발급 |
| QA-F05 | 쿠폰 템플릿 조회 IO 오류 → 503 COUPON_COMMUNICATION_FAILED, FAILED 저장 |
| QA-F06 | 쿠폰 발급 결과 조회 중 예상하지 못한 예외(`IllegalStateException`) → 500 COUPON_SYSTEM_ERROR(한글 메시지), FAILED 저장 (사유: `쿠폰 발급 결과 조회 실패 (IllegalStateException)`) |

## 7. 반복·동시 요청

| ID | 검증 항목 |
|---|---|
| QA-X01 | 같은 사용자·같은 미션 완료 20건 동시 요청 → 1건만 성공, 19건 COOLDOWN |
| QA-X02 | 서로 다른 사용자 130명 동시 참여 → 정확히 100건만 성공 |
| QA-X03 | 같은 참여 이력 보상 10건 동시 요청 → 1건 지급, 9건 ALREADY_GRANTED |
| QA-X04 | 한도 5개 쿠폰에 보상 10건 동시 요청 → 쿠폰 5개, NO_REWARD 5건 |

## 8. API 규격

| ID | 검증 항목 |
|---|---|
| QA-A01 | 비즈니스 시나리오 1~5단계를 API로 끝까지 실행 |
| QA-A02 | Content-Type `application/json;charset=UTF-8` |
| QA-A03 | 404 응답 `code`=영문 사유 코드, `msg`=한글 메시지, `data: null` (data 키가 있어야 함) |
| QA-A04 | 409 응답 (기간 외, 재참여 제한, 중복 보상) |
| QA-A05 | 보상 요청 전 조회 → 404 REWARD_NOT_FOUND |
| QA-A06 | 정의되지 않은 경로(404), 허용되지 않은 메서드(405)도 공통 포맷 |
| QA-A07 | NO_REWARD는 200 정상 응답 |

## 9. 요청값 검증

| ID | 검증 항목 |
|---|---|
| QA-V01 | 공백 userId → 400 MISSING_REQUIRED_VALUE, 한글 메시지에 필드명 포함 |
| QA-V02 | 공백 missionId → 400 MISSING_REQUIRED_VALUE, 참여 이력이 생기지 않음 |
| QA-V03 | 형식 제한 없음: 특수문자·공백·한글(`-`, 공백, 한글, `@`, `.`)이 있는 userId → 검증 통과 후 404 USER_NOT_FOUND (5건) |
| QA-V04 | 길이 제한 없음: 50자 초과 userId → 검증 통과 후 404 USER_NOT_FOUND |
| QA-V05 | 형식 제한 없음: 특수문자가 있는 missionId → 404 MISSION_NOT_FOUND, 참여 이력 생성 안 됨 |
| QA-V06 | 공백 participationNo → 400 MISSING_REQUIRED_VALUE (요청·조회) |
| QA-V07 | 존재하지 않는 이력번호(`abc`, `0`, `-1`, 미발급 번호) → 404 PARTICIPATION_NOT_FOUND (4건) |
| QA-V08 | 정상 발급된 이력번호로 결과 조회 → 요청 전이면 404 REWARD_NOT_FOUND |
| QA-V09 | 형식이 맞는 값은 기존 비즈니스 검증으로 이어짐 (없는 사용자 → 404 USER_NOT_FOUND) |

## 10. 비즈니스 키 채번 / 유니크 제약

| ID | 검증 항목 |
|---|---|
| QA-K01 | 채번기 동시 호출 300건(이력번호 150 + 리워드번호 150): 중복 없음, 형식 `(PT|RW)` + 일자 + 10자리 |
| QA-K02 | 사용자 100명 동시 미션 완료: 응답과 DB의 이력번호 100개가 모두 다름 |
| QA-K03 | 참여 이력 50건 보상 동시 요청: 리워드번호 50개 모두 다름 |
| QA-K04 | 같은 이력번호로 두 번 저장 → DB 유니크 제약 위반 |
| QA-K05 | 같은 리워드번호 / 같은 참여 이력에 보상 결과 두 번 저장 → DB 유니크 제약 위반 |
| QA-K06 | NO_REWARD 후 재요청하면 같은 리워드번호 유지 |

## 11. 실패·장애 상황

| ID | 구분 | 검증 항목 |
|---|---|---|
| QA-D01 | 애플리케이션 시스템 이슈 | 처리 중 예상하지 못한 예외 → 500 INTERNAL_SERVER_ERROR(한글 메시지, data=null), 참여 이력 생성 안 됨 |
| QA-D02 | DB 시스템 이슈 | DB 연결 실패 → 503 DATABASE_ERROR(한글 메시지), 참여 이력 생성 안 됨 |

쿠폰 통신 오류·보상 처리 실패 테스트는 6절(QA-F03~F06), 참여 기간 초과 테스트는 4절(QA-M13)에 있습니다.

## 12. 일자별 미션 조회 캐싱

| ID | 검증 항목 |
|---|---|
| QA-H01 | 같은 일자로 3번 조회 → DB 조회는 첫 번째 1번뿐(Hibernate 쿼리 실행 수 1 → 0 → 0), 이후 캐시에서 응답 |
| QA-H02 | 일자가 다르면 캐시 키가 달라 일자마다 1번씩 DB 조회 |
| QA-H03 | 캐시 만료 시간(TTL)이 저장 후 10분으로 설정됨 (미션 데이터 변경은 만료 후 반영, 별도 무효화 없음) |

## 13. 동시성 제어 락 성능

처리 시간·처리량은 실행 환경에 따라 달라지므로 로그(`[PERF]`)로 보고만 하고, 건수와 정합성만 검증합니다. 측정값은 `TEST_RESULT.md`에 있습니다.

| ID | 검증 항목 |
|---|---|
| QA-S01 | 같은 미션에 서로 다른 사용자 10/50/100/200명 동시 완료 요청 → 성공 min(요청 수, 100)건, 초과분은 `MISSION_TOTAL_LIMIT_EXCEEDED`, 락 대기 실패·기타 실패 0건, 사용자별 중복 참여 없음 |
| QA-S02 | 100명 동시 완료 후 이력마다 보상 요청 2번씩(200건) 동시 전송 → 보상 100건, 100건은 `REWARD_ALREADY_GRANTED`, 이력당 보상 1건, 발급 쿠폰 수 = 쿠폰 지급 보상 수 |
| QA-S03 | 같은 미션 100건 vs 서로 다른 미션 10개 × 10건 동시 완료 → 둘 다 100건 성공, 락 대기 실패 0건. 처리 시간 비교로 미션 락 병목 확인 |

## 14. 검증하지 못한 범위

- 여러 애플리케이션 인스턴스와 공유 DB 환경에서의 동시성 (단일 JVM, H2에서만 검증)
- 실제 HTTP 외부 쿠폰 시스템의 타임아웃·네트워크 오류 (재현체에서는 예외 주입으로만 확인)
- 대량 데이터에서의 목록 조회 성능
- 여러 인스턴스 간 미션 캐시 일관성 (로컬 캐시라 인스턴스별로 따로 동작)
- 캐시 만료(TTL 10분)가 실제 시간 경과로 일어나 미션 변경이 반영되는지 (설정값만 확인)
- 실제 운영 DB와 커넥션 풀 설정에서의 락 성능 (H2 In-memory, 커넥션 풀 기본 10개에서만 측정)
- 로그 출력 내용과 조회 쿼리의 인덱스 사용 여부는 자동화 테스트 대상에서 제외 (로그는 실제 서버 실행으로, 인덱스는 H2 `EXPLAIN`으로 한 번 확인. `TEST_RESULT.md` 참고)
