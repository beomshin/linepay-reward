# 테스트 실행 결과

## 0. 최신 실행 (교정 2 적용 후: 환경 설정 분리 · logback · MDC)

| 항목 | 값 |
|---|---|
| 실행일 | 2026-09-26 (KST) |
| 명령 | `gradlew.bat clean test bootJar` |
| 결과 | **87개 전체 통과** (기존 73 + 설정 분리 6 + 요청 추적 8) |

| 테스트 클래스 | QA | 테스트 수 | 실패 |
|---|---|---:|---:|
| `unit.KstTimeTest` | QA-T | 3 | 0 |
| `unit.ParticipationPolicyTest` | QA-P | 8 | 0 |
| `unit.FakeCouponSystemTest` | QA-C | 8 | 0 |
| `unit.TraceIdFilterTest` | QA-L | 5 | 0 |
| `config.ProfileConfigTest` | QA-E | 6 | 0 |
| `integration.MissionServiceTest` | QA-M | 12 | 0 |
| `integration.RewardServiceTest` | QA-R | 13 | 0 |
| `integration.CouponIssueFallbackTest` | QA-F | 2 | 0 |
| `integration.ConcurrencyTest` | QA-X | 4 | 0 |
| `api.RewardApiTest` | QA-A | 8 | 0 |
| `api.RequestValidationApiTest` | QA-V | 15 | 0 |
| `api.TraceIdLoggingTest` | QA-L | 3 | 0 |
| **합계** | | **87** | **0** |

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
| 프로파일별 설정 적용, 운영 프로파일에 테스트용 설정 없음 | 위 표 + QA-E01~E06 통과. prod는 기준 시각이 고정되지 않고 H2 콘솔이 꺼짐 |
| 개발은 콘솔, 운영은 로그 파일 | local·dev는 콘솔에 앱 로그 출력, prod는 콘솔에 앱 로그가 없고 `linepay-reward.log`에 기록 |
| 같은 요청은 같은 traceId, 요청 간 MDC 섞이지 않음 | QA-L01~L08 통과 (동시 요청 50건·스레드 재사용 포함). 실제 서버에서도 한 요청의 로그 3줄이 같은 traceId |

실행 중 발견한 사항

| 회차 | 결과 | 원인 | 조치 |
|---|---|---|---|
| 1회차 | 컴파일 실패 | 테스트의 `FilterChain` 람다 안에서 `Thread.sleep`의 `InterruptedException`을 처리하지 않음 | `LockSupport.parkNanos`로 교체 |
| 2회차 | 87/87 통과 | prod 기동 시 콘솔에 logback 경고 `Appender named [CONSOLE] not referenced` 출력 | 콘솔 appender 정의를 `!prod` 프로파일 블록 안으로 이동 |
| 3회차 | 87/87 통과 | prod 콘솔 경고 없음 확인 | - |

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
| `integration.CouponIssueFallbackTest` | QA-F | 2 | 0 |
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
| `integration.CouponIssueFallbackTest` | QA-F | 2 | 0 | 0.162 |
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
