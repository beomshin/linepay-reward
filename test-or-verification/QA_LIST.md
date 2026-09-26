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
| QA-F | `integration/CouponIssueFallbackTest` | 통합 (외부 오류 주입) |
| QA-X | `integration/ConcurrencyTest` | 통합 (동시성) |
| QA-A | `api/RewardApiTest` | API (MockMvc) |
| QA-V | `api/RequestValidationApiTest` | API (MockMvc, 요청값 검증) |
| QA-E | `config/ProfileConfigTest` | 단위 (프로파일 설정 분리) |
| QA-L | `unit/TraceIdFilterTest`, `api/TraceIdLoggingTest` | 단위 + API (MDC traceId, 로그) |

## 1. 시간 기준 (과제 9절, 프롬프트 9절)

| ID | 검증 항목 | 중요한 이유 |
|---|---|---|
| QA-T01 | UTC 15:00 → KST 다음 날 00:00으로 변환 | 일별 횟수가 KST 기준이어야 함 |
| QA-T02 | 일자 `yyyyMMdd`, 시간 `HHmmss` 상호 변환 | 저장 형식 요구사항 |
| QA-T03 | 나노초 절삭 (초 단위) | 저장 정밀도와 비교 정밀도를 맞춤 |

## 2. 참여 조건 경계값 (과제 6.2, 6.4)

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

## 3. 외부 쿠폰 시스템 계약 (과제 8절)

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

## 4. 미션 조회·완료 (과제 6절)

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

## 5. 보상 지급·조회 (과제 7절)

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

## 6. 쿠폰 발급 시점 실패 (과제 7.3, 8.2)

| ID | 검증 항목 |
|---|---|
| QA-F01 | 조회 때는 가능했지만 발급 때 한도 소진 → 쿠폰 제외 후 포인트로 재선정 |
| QA-F02 | 예상하지 못한 외부 오류 → COUPON_SYSTEM_ERROR(500), 보상 결과 저장 안 함 (롤백) |

## 7. 반복·동시 요청 (과제 5절)

| ID | 검증 항목 |
|---|---|
| QA-X01 | 같은 사용자·같은 미션 완료 20건 동시 요청 → 1건만 성공, 19건 COOLDOWN |
| QA-X02 | 서로 다른 사용자 130명 동시 참여 → 정확히 100건만 성공 |
| QA-X03 | 같은 참여 이력 보상 10건 동시 요청 → 1건 지급, 9건 ALREADY_GRANTED |
| QA-X04 | 한도 5개 쿠폰에 보상 10건 동시 요청 → 쿠폰 5개, NO_REWARD 5건 |

## 8. API 규격 (프롬프트 4.1, 4.2)

| ID | 검증 항목 |
|---|---|
| QA-A01 | 비즈니스 시나리오 1~5단계를 API로 끝까지 실행 |
| QA-A02 | Content-Type `application/json;charset=UTF-8` |
| QA-A03 | 404 응답 `code`=영문 사유 코드, `msg`=한글 메시지, `data: null` (data 키가 있어야 함) |
| QA-A04 | 409 응답 (기간 외, 재참여 제한, 중복 보상) |
| QA-A05 | 보상 요청 전 조회 → 404 REWARD_NOT_FOUND |
| QA-A06 | 경로 변수 형식 오류 → 400 INVALID_FORMAT |
| QA-A07 | 정의되지 않은 경로(404), 허용되지 않은 메서드(405)도 공통 포맷 |
| QA-A08 | NO_REWARD는 200 정상 응답 |

## 9. 요청값 검증 (교정 1: Spring Validation)

| ID | 검증 항목 |
|---|---|
| QA-V01 | 공백 userId → 400 MISSING_REQUIRED_VALUE, 한글 메시지에 필드명 포함 |
| QA-V02 | 공백 missionId → 400 MISSING_REQUIRED_VALUE, 참여 이력이 생기지 않음 |
| QA-V03 | 형식 제한 없음: 특수문자·공백·한글(`-`, 공백, 한글, `@`, `.`)이 있는 userId → 검증 통과 후 404 USER_NOT_FOUND (5건) |
| QA-V04 | 길이 제한 없음: 50자 초과 userId → 검증 통과 후 404 USER_NOT_FOUND |
| QA-V05 | 형식 제한 없음: 특수문자가 있는 missionId → 404 MISSION_NOT_FOUND, 참여 이력 생성 안 됨 |
| QA-V06 | 숫자가 아닌 participationId(`abc`, `1.5`) → 400 INVALID_FORMAT |
| QA-V07 | 0 이하 participationId(`0`, `-1`, `-9999`) → 400 OUT_OF_RANGE (요청·조회 모두, 3건) |
| QA-V08 | Long 범위를 넘는 숫자 → 400 INVALID_FORMAT |
| QA-V09 | 형식이 맞는 값은 기존 비즈니스 검증으로 이어짐 (없는 사용자 → 404 USER_NOT_FOUND) |

## 10. 환경 설정 분리 (교정 2)

| ID | 검증 항목 |
|---|---|
| QA-E01 | 공통 설정에 테스트용 설정 없음, 기본 프로파일 local |
| QA-E02 | local에만 기준 시각 고정(2026-09-01T12:00:00+09:00)과 H2 콘솔 |
| QA-E03 | dev는 기준 시각을 고정하지 않음 |
| QA-E04 | prod에 테스트용 설정 없음(H2 콘솔 비활성), 로그 파일 경로 있음 |
| QA-E05 | 기준 시각 설정이 없으면 시스템 현재 시각(KST) 사용 |
| QA-E06 | 기준 시각 설정이 있으면 해당 시각으로 고정 |

## 11. 요청 추적 로그 (교정 2: MDC traceId)

| ID | 검증 항목 |
|---|---|
| QA-L01 | 요청 처리 중 MDC에 traceId가 있고 응답 헤더 `X-Trace-Id`와 같음 |
| QA-L02 | 요청이 끝나면 MDC에서 traceId 제거 |
| QA-L03 | 처리 중 예외가 나도 MDC 제거, 예외는 그대로 전달 |
| QA-L04 | 요청마다 다른 traceId 발급 |
| QA-L05 | 동시 요청 50건(스레드 8개 재사용): 요청 안에서는 traceId 유지, 요청 간 섞이지 않음, 시작 시 이전 값 없음 |
| QA-L06 | 정상 요청의 `[REQ]`·`[MISSION]`·`[RES]` 로그에 같은 traceId, 요청 후 MDC 비워짐 |
| QA-L07 | 예외 요청의 `[EXC]` 로그에도 같은 traceId |
| QA-L08 | 연속 두 요청의 로그가 각자 traceId로 구분되고 섞이지 않음 |

## 12. 검증하지 못한 범위

- 여러 애플리케이션 인스턴스와 공유 DB 환경에서의 동시성 (단일 JVM, H2에서만 검증)
- 실제 HTTP 외부 쿠폰 시스템의 타임아웃·네트워크 오류 (재현체에서는 예외 주입으로만 확인)
- 대량 데이터에서의 목록 조회 성능
