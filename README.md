# LINE Pay 리워드 서비스

사용자가 미션을 수행하면 리워드 포인트 또는 쿠폰을 지급하는 백엔드 API입니다.

## 1. 기술 스택

| 항목 | 내용 |
|---|---|
| JDK | **Java 21** (21.0.10에서 검증) |
| Framework | Spring Boot 3.5.16 (spring-boot-starter-web, spring-boot-starter-data-jpa) |
| Build | Gradle 8.14.3 (Gradle Wrapper 포함) |
| Database | H2 In-memory |
| Test | JUnit 5 (spring-boot-starter-test) |

그 밖의 외부 라이브러리는 추가하지 않았습니다.

## 2. 프로젝트 구조

```
.
├── README.md / DESIGN.md / AI_USAGE.md / api-spec.md
├── build.gradle, settings.gradle, gradlew, gradlew.bat, gradle/wrapper/
├── source-code/                       # 애플리케이션 코드
│   └── src/main/
│       ├── java/com/linepay/reward/
│       │   ├── common/     (공통 응답, ErrorCode, 전역 예외 처리, KST 시간, Clock 설정)
│       │   ├── user/       (사용자)
│       │   ├── mission/    (미션, 보상 아이템, 참여 이력, 참여 정책, 미션 API)
│       │   ├── reward/     (보상 지급/조회, 보상 API)
│       │   └── coupon/     (외부 쿠폰 시스템 계약 + In-memory 재현체)
│       └── resources/ (application.yml, data.sql = Seed Data)
└── test-or-verification/              # 테스트 및 검증 자료
    ├── src/test/java/...              # JUnit5 자동화 테스트
    ├── QA_LIST.md                     # QA 항목과 테스트 매핑
    ├── TEST_RESULT.md                 # 테스트 실행 결과
    └── api-verification.http          # IntelliJ HTTP Client 수동 검증 스크립트
```

제출물 구조(과제 16절)에 맞추기 위해 `build.gradle`에서 소스 경로를 다시 지정했습니다.
(`main` → `source-code/src/main`, `test` → `test-or-verification/src/test`)

## 3. 실행 방법

```bash
# macOS / Linux
./gradlew bootRun

# Windows
gradlew.bat bootRun
```

- 서버: `http://localhost:8080`
- H2 Console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:linepay`, user `sa`)
- 시작할 때 `data.sql`로 Seed Data(User, Mission, Mission Item)를 적재합니다. 쿠폰 템플릿은 외부 쿠폰 재현체(`FakeCouponSystem`)가 가지고 시작합니다.

### 기준 시각

`application.yml`의 `linepay.clock.fixed-at`로 서비스 기준 시각을 **2026-09-01T12:00:00+09:00**(과제 9절 검증 기준 시각)에 고정했습니다.
값을 비우면 시스템 현재 시각(KST)을 사용합니다.

```yaml
linepay:
  clock:
    fixed-at: 2026-09-01T12:00:00+09:00   # 비우면 시스템 시각
```

> 시각이 고정되어 있으므로 실행 중에는 시간이 흐르지 않습니다. 같은 사용자가 같은 미션을 두 번째로 완료하면 1시간 재참여 제한에 걸립니다. 시간 경과 시나리오는 자동화 테스트에서 `MutableClock`으로 검증합니다.

## 4. 테스트 실행 방법

```bash
./gradlew test          # Windows: gradlew.bat test
```

- 결과 리포트: `build/reports/tests/test/index.html`
- 테스트 목록과 의도: `test-or-verification/QA_LIST.md`
- 최근 실행 결과: `test-or-verification/TEST_RESULT.md`

## 5. 구현한 기능

| 기능 | API |
|---|---|
| 특정 사용자 기준 참여 가능한 미션 목록 조회 | `GET /linepay/v1/mission/{userId}` |
| 미션 수행 완료 처리 | `POST /linepay/v1/mission/{userId}/{missionId}/complete` |
| 완료된 미션 참여에 대한 보상 지급 요청 | `POST /linepay/v1/reward/{userId}/{participationId}` |
| 보상 지급 결과 조회 | `GET /linepay/v1/reward/{userId}/{participationId}` |

비즈니스 정책

- 미션 참여: 참여 기간(`start <= now < end`), 미션 전체 최대 100회, 사용자별 하루(KST) 최대 10회, 직전 참여 후 1시간 경과
- 보상: 참여 이력 1건당 보상 1회, 보상 아이템 중 무작위 선택, 포인트 5~10 무작위, 쿠폰은 한도 소진·발급 중지 시 제외, 지급 가능한 보상이 없으면 `NO_REWARD` 반환 후 재요청 허용
- 반복·동시 요청: 미션 단위(완료 처리), 참여 이력 단위(보상 지급)로 DB 비관적 락을 걸어 정책이 깨지지 않도록 처리
- 외부 쿠폰 시스템: `CouponClient` 계약 + `FakeCouponSystem` 재현체 (멱등 requestId, 한도 소진, 유효하지 않은 템플릿, 404)

API 상세는 [api-spec.md](api-spec.md)를 참고해 주세요.

## 6. 가정한 내용

- 인증은 구현하지 않았고, PathVariable `userId`가 존재하는 사용자인지만 확인합니다. (과제 11절)
- 다른 사용자의 참여 이력에 보상을 요청하거나 조회하면, 참여 이력의 존재 여부를 드러내지 않도록 `PARTICIPATION_NOT_FOUND`(404)를 반환합니다.
- 미션 수행 확인은 외부에서 끝난 것으로 보고, 완료 요청이 들어오면 참여 조건만 검사합니다. (과제 5절)
- "지급 가능한 보상 없음"은 오류가 아닌 정상 결과로 보고 HTTP 200과 `rewardStatus = NO_REWARD`로 응답합니다.
- 이미 지급된 참여 이력에 다시 보상을 요청하면 409 `REWARD_ALREADY_GRANTED`를 반환합니다. 기존 결과는 결과 조회 API로 확인할 수 있습니다.
- 시간은 KST 기준이며, 일자(`yyyyMMdd`)와 시간(`HHmmss`)을 컬럼으로 나눠 초 단위로 저장합니다.
- 쿠폰 템플릿(10.4)은 외부 시스템이 가진 데이터로 보고, 리워드 서비스 DB가 아닌 쿠폰 재현체에 둡니다.
- 존재하지 않는 쿠폰 템플릿(404)은 "지급할 수 없는 쿠폰"으로 보고 후보에서 제외합니다.

## 7. 알려진 제약사항

- In-memory H2를 쓰므로 애플리케이션을 재시작하면 참여·보상 이력이 초기화됩니다. 쿠폰 재현체도 메모리에 상태를 둡니다.
- 동시성 제어는 단일 DB의 행 락(`SELECT ... FOR UPDATE`)에 의존합니다. 같은 미션에 대한 완료 요청은 직렬로 처리되므로, 인기 미션에서는 처리량이 제한될 수 있습니다.
- 외부 쿠폰 발급 호출이 DB 트랜잭션(락) 안에서 이뤄집니다. 실제 외부 연동에서 응답이 느려지면 락을 쥐는 시간도 길어집니다.
- 기준 시각이 고정(`fixed-at`)된 상태에서는 실행 중 시간이 흐르지 않습니다.
