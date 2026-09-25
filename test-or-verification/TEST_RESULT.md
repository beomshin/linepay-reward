# 테스트 실행 결과

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
