# LINE Pay 리워드 서비스

사용자가 미션을 수행하면 리워드 포인트 또는 쿠폰을 지급하는 백엔드 API입니다.

## 1. 기술 스택

| 항목 | 내용 |
|---|---|
| JDK | **Java 21** (21.0.10에서 검증) |
| Framework | Spring Boot 3.5.16 (spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation) |
| Build | Gradle 8.14.3 (Gradle Wrapper 포함) |
| Database | H2 In-memory |
| Test | JUnit 5 (spring-boot-starter-test) |
| 코드 간소화 | Lombok (Spring Boot 관리 버전, 컴파일 시점에만 사용) |

- Lombok: 엔티티 Getter·기본 생성자, `@RequiredArgsConstructor` 생성자 주입, `@Slf4j` 로거 (엔티티에 `@Data`·`@Setter`는 쓰지 않음)
- Spring Validation: 경로 변수(`userId`, `missionId`, `participationId`) 검증
- IntelliJ에서 열 때는 Lombok 플러그인과 Annotation Processing이 켜져 있어야 합니다. (Gradle 빌드/테스트는 설정 없이 동작)

## 2. 프로젝트 구조

```
.
├── README.md / DESIGN.md / AI_USAGE.md / api-spec.md
├── build.gradle, settings.gradle, gradlew, gradlew.bat, gradle/wrapper/
├── source-code/                       # 애플리케이션 코드
│   └── src/main/
│       ├── java/com/linepay/reward/
│       │   ├── common/     (공통 응답, ErrorCode, 전역 예외 처리, KST 시간, Clock 설정, 요청 추적 필터)
│       │   ├── user/       (사용자)
│       │   ├── mission/    (미션, 보상 아이템, 참여 이력, 참여 정책, 미션 API)
│       │   ├── reward/     (보상 지급/조회, 보상 API)
│       │   └── coupon/     (외부 쿠폰 시스템 계약 + In-memory 재현체)
│       └── resources/ (application.yml + application-{local,dev,prod}.yml, logback-spring.xml, data.sql = Seed Data)
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
# macOS / Linux (프로파일 미지정 시 local)
./gradlew bootRun

# Windows
gradlew.bat bootRun

# 프로파일 지정 (local | dev | prod)
./gradlew bootRun --args='--spring.profiles.active=prod'
java -jar build/libs/linepay-reward-0.0.1.jar --spring.profiles.active=prod
```

- 서버: `http://localhost:8080`
- H2 Console (local, dev만): `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:linepay`, user `sa`)
- 시작할 때 `data.sql`로 Seed Data(User, Mission, Mission Item)를 적재합니다. 쿠폰 템플릿은 외부 쿠폰 재현체(`FakeCouponSystem`)가 가지고 시작합니다.

### 프로파일별 설정

| 프로파일 | 설정 파일 | 기준 시각 | H2 콘솔 | 로그 출력 |
|---|---|---|---|---|
| (공통) | `application.yml` | - | - | - |
| local (기본) | `application-local.yml` | 2026-09-01T12:00:00+09:00 고정 | 사용 | 콘솔 |
| dev | `application-dev.yml` | 시스템 현재 시각(KST) | 사용 | 콘솔 |
| prod | `application-prod.yml` | 시스템 현재 시각(KST) | 사용 안 함 | 파일 `./logs/linepay-reward.log` (일자별 롤링, 30일 보관) |

- 테스트용 설정(기준 시각 고정, H2 콘솔)은 prod 프로파일에 두지 않습니다.
- prod 로그 경로는 `--logging.file.path=<경로>`로 바꿀 수 있습니다.

### 요청 추적 로그 (traceId)

- 요청마다 traceId를 만들어 MDC에 넣고, 모든 로그 라인에 `[traceId]`로 출력합니다. 같은 값을 응답 헤더 `X-Trace-Id`로도 돌려줍니다.
- 로그는 한글로 남기며, 접두어로 단계를 구분합니다.

| 접두어 | 내용 |
|---|---|
| `[REQ]` / `[RES]` / `[ERR]` | API 요청 시작, 요청 종료(상태 코드·처리 시간), 필터까지 올라온 예외 |
| `[USER]` | 사용자 존재 여부 DB 조회 |
| `[MISSION]` | 미션 조회, 미션 락 획득, 참여 조건 검사(전체·당일 참여 수, 직전 참여), 참여 이력 저장 |
| `[REWARD]` | 참여 이력 락 획득, 기존 보상 조회, 보상 아이템 조회, 후보 확정, 무작위 선택, 결과 저장 |
| `[COUPON]` | 외부 쿠폰 템플릿 조회, 기존 발급 결과 조회, 쿠폰 발급 요청·성공·거절 |
| `[EXC]` | 비즈니스 거절, 요청값 검증 실패, 서버 오류 |

미션 완료 → 보상 지급 요청 로그 예 (traceId 일부 생략)

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

### 기준 시각 (local)

`application-local.yml`의 `linepay.clock.fixed-at`로 서비스 기준 시각을 **2026-09-01T12:00:00+09:00**(과제 9절 검증 기준 시각)에 고정했습니다.
값이 없으면(dev, prod) 시스템 현재 시각(KST)을 사용합니다.

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
- 요청값 검증: 필수값 누락·형식 오류·범위 오류를 400과 영문 코드(`MISSING_REQUIRED_VALUE`/`INVALID_FORMAT`/`OUT_OF_RANGE`) + 한글 메시지로 응답
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

- prod 프로파일도 과제 실행 조건(외부 인프라 없음)에 맞춰 In-memory H2와 Seed Data를 그대로 씁니다. 실제 운영에서는 DB 연결 정보를 prod 설정에 따로 두어야 합니다.
- In-memory H2를 쓰므로 애플리케이션을 재시작하면 참여·보상 이력이 초기화됩니다. 쿠폰 재현체도 메모리에 상태를 둡니다.
- 동시성 제어는 단일 DB의 행 락(`SELECT ... FOR UPDATE`)에 의존합니다. 같은 미션에 대한 완료 요청은 직렬로 처리되므로, 인기 미션에서는 처리량이 제한될 수 있습니다.
- 외부 쿠폰 발급 호출이 DB 트랜잭션(락) 안에서 이뤄집니다. 실제 외부 연동에서 응답이 느려지면 락을 쥐는 시간도 길어집니다.
- 기준 시각이 고정(`fixed-at`)된 상태에서는 실행 중 시간이 흐르지 않습니다.
