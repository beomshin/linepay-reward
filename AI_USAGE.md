# AI 활용 기록 (AI_USAGE.md)

## 13.1 AI 활용 개요

| 항목 | 내용 |
|---|---|
| 사용한 AI 도구와 모델 | Claude Opus |
| AI를 활용한 주요 작업 | 바이브 코딩으로 프로젝트 구성 |
| AI에 맡긴 역할 | 과제 문서를 바탕으로 프로젝트 구조와 기능 구현 |
| 본인이 직접 판단하고 결정한 영역 | 최초 프롬프트 작성 및 개선 사항 검토, 수정 |
| 여러 AI 도구를 사용한 경우 각 도구의 역할 | IntelliJ와 Claude를 MCP로 연동해 프로젝트를 자동으로 구성 |

## 13.2 최초 작업 위임

| 항목 | 내용 |
|---|---|
| 최초 작업 지시 원문 | 아래 "최초 작업 지시 원문" 참고 (과제 안내 HTML과 함께 전달) |
| AI에 기대한 결과 | 현재 프롬프트로 전체 기능이 동작하는 프로젝트 완성 |
| AI가 제안하거나 생성한 초기 결과 요약 | **(AI 작성)** 아래 "AI 초기 결과 요약" 참고 |

### 최초 작업 지시 원문

세션 시작 명령: `전달된 테스트 문서와 최초 프롬프트 파일 기반으로 연동된 인텔리제이에 프로젝트 구현`

첨부한 최초 프롬프트 파일(`init_prompt.md`) 원문:

````markdown
# 역할
너는 백엔드 개발자다.
첨부한 과제 문서 근거로 리워드·미션 시스템을 구현한다.
과제 문서에 없는 내용을 임의로 설정하지 않는다.
적절한 주석을 통하여 개발자가 이해하기 쉽게 작성한다.

---

# 1. 과제 개요
과제 문서의 내용을 따른다.

# 2. 확인하고자 하는 역량
과제 문서의 내용을 따른다.

# 3. 기술 및 실행 조건
Java 21, Spring Boot, Gradle, H2, JUnit5을 사용하며, 그밖 의존성은 임의로 추가하지 않는다.

# 4. 기본 구현 범위
과제 문서의 기능 범위를 구현하고, API는 아래 규격을 따른다.

## 4.1 API 공통 규격
| 항목 | 규격 |
|---|---|
| Base Path | `/linepay/{version}/{mission \| reward}` (`userId`는 필요한 API에만 사용) |
| 버전 | `v1` (예: `/linepay/v1/mission/{userId}`) |
| Content-Type | `application/json; charset=UTF-8` |
| 메서드 | 조회는 `GET`, 미션 완료 처리와 보상 지급 요청은 `POST` |
| 사용자 식별 | PathVariable `{userId}` |

## 4.2 응답 형식
- 성공과 실패 응답 모두 `code`와 `msg`를 반드시 포함한다.
- 실패 응답에서 `data`는 `null`이다.
- 결과에 맞는 HTTP 상태 코드를 쓴다. (예: 200 성공, 400 잘못된 요청, 404 대상 없음, 409 중복·상태 충돌, 500 서버 오류)
- 에러 코드는 enum으로 정의하고, 전역 예외 핸들러(`@RestControllerAdvice`)에서 한 번에 처리한다.

기본 형식:
```json
{
  "code": "0000",
  "msg": "SUCCESS",
  "data": { }
}
```

실패 예시:
```json
{
  "code": "E404",
  "msg": "MISSION_NOT_FOUND",
  "data": null
}
```

# 5. 비즈니스 시나리오
과제 문서의 내용을 따른다.

# 6. 미션 참여 정책
과제 문서의 내용을 따른다.

# 7. 보상 지급 정책
과제 문서의 내용을 따른다.

# 8. 외부 쿠폰 시스템
과제 문서의 내용을 따른다.

# 9. 시간 기준
과제 문서의 시간 정책을 따르고, 아래 규칙을 추가로 적용한다.
- 일자와 시간은 컬럼(필드)을 나눠 관리한다.
  - 일자: `YYYYMMDD` (예: `20260923`)
  - 시간: `HHmmss` (예: `143005`)

# 10. Seed Data
과제 문서의 내용을 따른다.

# 11. 보안 및 인증 관련 가정
과제 문서의 내용을 따른다.

# 12. 추가로 발견한 문제와 구현 범위
이번 요청에서는 제외한다.

# 13. AI 활용 요구사항

## 13.1 AI 활용 개요
| 항목 | 내용 |
|---|---|
| 사용한 AI 도구와 모델 | Claude Opus |
| AI를 활용한 주요 작업 | 바이브 코딩으로 프로젝트 구성 |
| AI에 맡긴 역할 | 과제 문서를 바탕으로 프로젝트 구조와 기능 구현 |
| 본인이 직접 판단하고 결정한 영역 | 최초 프롬프트 작성 |
| 여러 AI 도구를 사용한 경우 각 도구의 역할 | IntelliJ와 Claude를 MCP로 연동해 프로젝트를 자동으로 구성 |

## 13.2 최초 작업 위임
| 항목 | 내용 |
|---|---|
| 최초 작업 지시 원문 | 현재 프롬프트 |
| AI에 기대한 결과 | 현재 프롬프트로 전체 기능이 동작하는 프로젝트 완성 |
| AI가 제안하거나 생성한 초기 결과 요약 | **(AI 작성)** 구현을 마친 뒤, 생성한 프로젝트 구조, 주요 클래스, 구현한 API 목록, 적용한 설계 결정을 요약해 이 칸을 채운다. |

## 13.3 초기 결과 검토
이번 요청에서는 제외한다.

## 13.4 주요 교정 기록
이번 요청에서는 제외한다.

## 13.5 최종 회고
이번 요청에서는 제외한다.

# 14. 중요 문제와 우선순위
이번 요청에서는 제외한다.

# 15. 테스트 및 검증
- 생성한 코드는 테스트 코드로 검증한다. (JUnit 5 기준)
- 모든 테스트가 통과해야 작업을 완료한 것으로 본다.
- 생성된 로직을 기준으로 판단하여 QA 리스트를 작성 후 테스트한다.

# 16. 제출물
과제 문서의 내용을 따라 프로젝트 구성을 만든다.

# 17. 평가 안내
제외 대상이다.

# 18. 참고 및 유의사항
제외 대상이다.
````

### AI 초기 결과 요약 (AI 작성)

**1) 프로젝트 구조**

- 제출물 구조(과제 16절)에 맞춰 루트에 Gradle 프로젝트를 두고, `build.gradle`에서 소스 경로를 `source-code/src/main`(애플리케이션)과 `test-or-verification/src/test`(테스트)로 다시 지정했습니다.
- 기술 스택: Java 21, Spring Boot 3.5.16(web, data-jpa), Gradle 8.14.3 Wrapper, H2 In-memory, JUnit5. 그 밖의 의존성은 추가하지 않았습니다.
- 패키지: `common`(응답·예외·시간·설정), `user`, `mission`, `reward`, `coupon`

**2) 주요 클래스**

| 클래스 | 역할 |
|---|---|
| `ApiResponse` | `{code, msg, data}` 공통 응답. 실패 시 data = null |
| `ErrorCode` / `GlobalExceptionHandler` | 에러 코드 enum(`code = "E" + HTTP 상태`), `@RestControllerAdvice` 일괄 처리 |
| `KstTime` / `ClockConfig` | KST 기준 일자(`yyyyMMdd`)·시간(`HHmmss`) 분리, 기준 시각 고정 가능한 Clock |
| `Mission`, `MissionItem`, `MissionParticipation`, `Reward`, `User` | JPA 엔티티 (일자/시간 컬럼 분리) |
| `ParticipationPolicy` | 기간 / 전체 100회 / 일 10회 / 1시간 재참여 규칙 (조회·완료가 함께 사용) |
| `MissionService` | 참여 가능 미션 조회, 미션 완료 처리 (미션 행 비관적 락) |
| `RewardService` | 보상 선정·지급·결과 조회 (참여 이력 행 비관적 락, 쿠폰 제외·재선정·복구) |
| `RewardRandomizer` | 보상 아이템 무작위 선택, 포인트 5~10 |
| `CouponClient` / `FakeCouponSystem` | 외부 쿠폰 계약 인터페이스와 In-memory 재현체 |

**3) 구현한 API**

| Method | Path | 기능 |
|---|---|---|
| GET | `/linepay/v1/mission/{userId}` | 참여 가능한 미션 목록 조회 |
| POST | `/linepay/v1/mission/{userId}/{missionId}/complete` | 미션 수행 완료 처리 |
| POST | `/linepay/v1/reward/{userId}/{participationId}` | 보상 지급 요청 |
| GET | `/linepay/v1/reward/{userId}/{participationId}` | 보상 지급 결과 조회 |

**4) 적용한 설계 결정**

- 참여 조건 규칙을 `ParticipationPolicy` 한곳에 모아, 목록 조회와 완료 처리의 판단이 서로 어긋나지 않게 했습니다.
- 과제 5절(반복·동시 요청)에 대응해, 미션 완료는 미션 행, 보상 지급은 참여 이력 행에 `SELECT ... FOR UPDATE` 락을 걸었습니다. `reward.participation_id` UNIQUE 제약으로 한 번 더 막습니다.
- 쿠폰 발급 requestId를 `REWARD_{participationId}_{couponTemplateId}`로 정해, 재시도해도 외부 시스템에서 중복 발급되지 않게 했습니다. 외부 발급은 됐지만 저장 전에 실패한 경우 8.3 조회로 복구합니다.
- 발급 시점에 쿠폰이 거절되면(한도 소진·유효하지 않은 템플릿) 해당 쿠폰을 후보에서 빼고 다시 선정합니다. 후보가 없으면 `NO_REWARD`(200)를 반환하고 재요청을 허용합니다.
- 기준 시각을 `Clock` 빈으로 주입해, 과제 9절 검증 기준 시각으로 고정하거나 테스트에서 시간을 옮길 수 있게 했습니다.
- 외부 쿠폰 시스템은 HTTP 서버 없이 계약 인터페이스 + In-memory 재현체로 구현했습니다. (과제 8절)

**5) 검증**

- QA 리스트(`test-or-verification/QA_LIST.md`)를 작성하고 JUnit5 테스트 58개로 검증했습니다. 연동된 IntelliJ 환경에서 `gradlew test`를 실행해 모두 통과했습니다. (`test-or-verification/TEST_RESULT.md`)
- 첫 실행에서 58개 중 1개가 실패했습니다. 원인은 기능 결함이 아니라 테스트 단언 방식이었습니다. JsonPath `exists()`는 값이 `null`인 필드를 "없음"으로 판단합니다. 응답 JSON을 직접 파싱해 `data` 키가 있고 값이 `null`인지 확인하도록 테스트를 고친 뒤 모두 통과했습니다.

## 13.3 초기 결과 검토

> 초기 결과란 최초 작업 지시 이후 AI가 처음으로 완료했다고 보고한 계획 또는 구현 결과를 의미합니다.
>
> 검토 대상: 13.2의 "AI 초기 결과 요약"에 정리된 첫 구현 결과 (API 4개, JUnit5 테스트 58개 통과 보고 시점)
> 
> 검토 내용을 1차적으로 수기 작성한 후 AI를 활용하여 보완하며 적용하였습니다.


### 1. 요구사항을 충족한다고 판단한 부분

| No | 항목 | 검토 내용 |
|:--:|------|-----------|
| 1 | 기본 서비스 로직 동작 | 미션 참여, 완료, 보상 지급으로 이어지는 기본 서비스 로직이 요구사항대로 동작함 |
| 2 | 각 기능별에 따른 아키텍처 구성 | 기능 단위로 계층(Controller / Service / Repository 등)이 구분되어 구성됨 |
| 3 | 요청된 API 형식에 따른 구현 | 요구사항에 명시된 API 요청·응답 형식에 맞추어 구현됨 |
| 4 | Java 버전에 맞는 기술 접목 | 사용 중인 Java 버전에서 제공하는 문법과 기능을 적절히 활용함 |
| 5 | 인터페이스의 적절한 사용 (쿠폰) | 쿠폰 발급 연동부를 인터페이스로 추상화하여 구현체 교체가 가능한 구조로 작성됨 |
| 6 | Policy 정책 소스 단일화 | 참여 정책 관련 로직이 `ParticipationPolicy`로 모여 있어 정책 파악이 용이함 |
| 7 | Enum을 통한 상수 관리 | 상태값, 유형 등 상수를 Enum으로 관리하여 하드코딩을 줄임 |
| 8 | 90% 이상 테스트케이스 완성 | 주요 기능에 대한 테스트케이스가 90% 이상 작성됨 |

### 2. 부족하거나 잘못됐다고 판단한 부분

| No | 항목 | 검토 내용 | 관련 위치 |
|:--:|------|-----------|-----------|
| 1 | 환경 설정 분리 작업 | 환경 설정이 환경별로 분리되어 있지 않음. 예를 들어 테스트용 기준 시각 설정은 운영 환경에 세팅되면 안 되므로 Profile 기준으로 환경별 설정 분리가 필요함 | `application.yml` (`linepay.clock.fixed-at`) |
| 2 | logback 설정 파일 작업 | logback 설정 파일이 없음. 개발 환경은 시스템 콘솔 출력, 운영 환경은 파일 출력으로 처리되도록 설정 필요 | `source-code/src/main/resources` |
| 3 | 데이터 모델링 보완 작업 | 데이터 모델의 PK를 기준으로 로직이 구성되어 있음. 운영상 필요한 리워드번호, 이력번호 등 PK가 아닌 유니크한 값을 생성하여 처리하도록 모델링과 로직 수정 필요. 특히 이력번호 채번은 반드시 유니크해야 함 | `MissionParticipation`, `Reward` |
| 4 | 서비스 운영 편의 적용 | • 응답 CODE는 성공 및 실패 사유를 영문 코드로 표시하고, 메시지는 클라이언트가 쉽게 이해할 수 있는 한글로 응답하도록 수정 필요<br>• Spring Validation을 통해 클라이언트 요청 값 검증 필요<br>• `ParticipationPolicy` 정책 소스는 잦은 변경이 발생할 수 있으므로 정책별로 개별 분리하고 빈으로 등록하여 사용하도록 변경 필요 | `ErrorCode`, `ApiResponse`, Controller, `ParticipationPolicy` |
| 5 | 데이터 증가에 따른 처리 필요 | • `findAllByOrderByMissionIdAsc`: 미션 데이터 증가 시 전체 조회로 성능 이슈 발생 가능. 인덱스 및 조회 조건 재설계 필요<br>• `countByMissionIdAndUserIdAndParticipatedDate`: 참여 데이터 증가 시 성능 이슈 발생 가능. 인덱스 설계 후 재적용 필요<br>• `findByMissionIdOrderByMissionItemIdAsc`: 불필요한 정렬로 리소스를 사용하고 있어 정렬 제거 검토 필요 | `MissionRepository`, `MissionParticipationRepository`, `MissionItemRepository` |
| 6 | 예외 처리 케이스 | 쿠폰 발급 로직에서 `CouponApiException` 외의 Exception은 처리되지 않아 장애로 이어질 수 있음. 예외 케이스를 검토하여 보완 필요 | `RewardService` |
| 7 | 장애 포인트 사전 방지 | 보상 candidates를 while문으로 선정하고 있어, 추후 개발 과정에서 조건이 변경되면 무한루프에 빠질 잠재적 위험이 있음. 반복 횟수가 명확한 for문으로 변경 필요 | `RewardService.grantOrMarkNoReward` |
| 8 | 주석 및 로그 처리 | 수행되는 API의 동작을 확인할 수 있는 로그가 부족함. 로그를 적용하고 각 요청 단위로 추적할 수 있도록 MDC 설정 필요 | Controller, Service 전반 |

### 3. 불필요하거나 과도하다고 판단한 부분

| No | 항목 | 검토 내용 | 관련 위치 |
|:--:|------|-----------|-----------|
| 1 | 과도한 JPA 문법 사용 | `findFirstByMissionIdAndUserIdOrderByParticipatedDateDescParticipatedTimeDescParticipationIdDesc`와 같이 과도하게 긴 JPA 메소드명은 가독성이 떨어지고, 추후 테이블·컬럼 네이밍 변경 등의 이슈에 대응하기 어려움. JPQL로 전환하고 해당 조회의 인덱스 설계를 확인 필요 | `MissionParticipationRepository` |
| 2 | 가짜 쿠폰 발급 서비스 테스트 코드 | Junit 테스트를 위해 가짜(Fake) 쿠폰 발급 서비스 코드가 별도로 작성되어 있어 필요 이상으로 과도하다고 판단함 | `FakeCouponSystemTest` |

### 4. 추가 확인이 필요하다고 판단한 부분

| No | 항목 | 검토 내용 |
|:--:|------|-----------|
| 1 | 동시성 제어 락에 따른 성능 저하 | 적용된 동시성 제어 락 방식이 트래픽 증가 시 병목이 되지 않는지 확인 필요 |
| 2 | 전체 데이터 조회에 따른 성능 저하 | 전체 데이터를 조회하는 로직이 데이터 증가 시 성능 저하를 일으키지 않는지 확인 필요 |
| 3 | 코드 간소화를 위한 라이브러리 사용 | • Lombok 라이브러리를 추가하여 코드 간소화<br>• 생성자 빈 주입 방식을 적용하여 코드 간소화 |
| 4 | 유지보수를 위한 주석 추가 | 주요 비즈니스 로직과 정책 코드에 유지보수를 위한 주석 추가 여부 확인 필요 |
| 5 | QA 필요 항목 추가 | 아래 실패 케이스에 대한 테스트가 부족하여 추가 필요<br>• 애플리케이션 시스템 이슈에 대한 실패 테스트<br>• DB 시스템 이슈에 대한 실패 테스트<br>• 미션 참여 가능 기간 초과 상태에서의 완료 요청 실패 테스트<br>• 쿠폰 발급 통신 오류 실패 혹은 리워드 보상 테스트 |
| 6 | 쿠폰 IO Exception 처리 | 쿠폰 발급 외부 통신 중 IO Exception 발생 시 처리 방식(재시도, 보상 처리 등) 확인 필요 |
| 7 | 대용량 트래픽 조회 캐싱 처리 | 조회 빈도가 높은 데이터에 대해 대용량 트래픽 대비 캐싱 적용 여부 확인 필요 |

### 5. 검토 후 가장 중요하게 선택한 문제

| 우선순위 | 항목 | 선택 이유 |
|:--:|------|-----------|
| 1 | 장애 포인트 로직 확인 | 쿠폰 발급 시 처리되지 않는 Exception, while문 무한루프 가능성 등은 운영 중 서비스 장애로 직결되므로 최우선으로 보완 필요 |
| 2 | 동시성 제어 | 미션 참여 및 보상 지급은 중복 처리 시 데이터 정합성 문제가 발생하므로, 동시성 제어의 정확성과 성능을 함께 확인 필요 |
| 3 | 데이터 모델링 보완 및 인덱스 보완 | PK 기반 로직을 유니크한 비즈니스 키(리워드번호, 이력번호) 기반으로 전환하고, 데이터 증가에 대비한 인덱스 설계 필요 |
| 4 | JPA 무분별 사용 방지 | 과도한 JPA 메소드명과 불필요한 정렬·전체 조회는 유지보수성과 성능을 동시에 떨어뜨리므로 JPQL 전환 및 조회 조건 재설계 필요 |

## 13.4 주요 교정 기록

> 
> AI를 활용하여 교정 기록 작성 후 수기 검토, 수정 진행하였습니다.


### 교정 기록 1. 코드 최적화 (Lombok · 생성자 주입 · 요청값 검증)

#### 1) 교정 기록 제목

코드 최적화: Lombok 적용, `@RequiredArgsConstructor` 생성자 주입 통일, Spring Validation 요청값 검증 도입

#### 2) 교정이 필요하다고 판단한 이유

- Getter·생성자 등 반복 코드가 많아 가독성이 떨어지고, 필드를 추가하거나 바꿀 때 수정이 누락될 위험이 있음
- 의존성 주입 방식을 코드 규칙으로 통일해 불변성(`final`)과 테스트 용이성을 확보할 필요가 있음
- 요청값 검증이 선언적으로 이루어지지 않아, 잘못된 값(공백, 숫자가 아닌 값, 0 이하 값 등)이 서비스 로직까지 전달될 수 있음
- 13.3 초기 결과 검토의 "부족한 부분 4번(서비스 운영 편의: 영문 코드 + 한글 메시지, Spring Validation)"과 "추가 확인 3번(Lombok, 생성자 주입)"에 대한 후속 조치

#### 3) AI에 전달한 후속 지시 원문

세션 지시: `전달된 프롬프트를 프로젝트 적용 개발 진행하기`

첨부한 교정 프롬프트(`교정_1_코드최적화_프롬프트.md`) 원문:

````markdown
# 코드 최적화 (Lombok · 생성자 주입 · 요청값 검증)

검증된 표준 라이브러리와 Spring 권장 방식을 적용하여, 기능 변경 없이 코드 품질과 유지보수성을 개선해 주세요.

## 교정 사유
1. Getter·생성자 등 반복 코드가 많아 가독성이 떨어지고 수정 누락 위험이 있음
2. 의존성 주입 방식이 일관되지 않아 불변성 보장과 테스트 용이성이 부족함
3. 요청값 검증이 선언적으로 이루어지지 않아 잘못된 값이 서비스 로직까지 전달될 수 있음

## 지시 사항
1. Lombok을 적용하되, JPA 엔티티에는 `@Data`, `@Setter` 사용을 금지하고 안전한 어노테이션만 사용
2. 모든 의존성을 `private final` + `@RequiredArgsConstructor` 생성자 주입으로 통일
3. Spring Validation으로 요청값을 검증하고, 실패 시 영문 코드 + 한글 메시지로 응답하며 검증 실패 테스트 추가

## 완료 조건
1. 전체 테스트를 실행하여 기존 테스트케이스 통과 여부 확인
2. 필수값 누락, 형식 오류, 범위 오류 시 400 응답과 정의된 코드·메시지 반환 확인
3. 애플리케이션 기동 시 빈 주입 오류 발생 여부 확인
````


#### 4) AI의 결과

| 지시 사항 | AI 결과 |
|---|---|
| Lombok 적용 | `build.gradle`에 Lombok(compileOnly + annotationProcessor) 추가. 엔티티 5개(`User`, `Mission`, `MissionItem`, `MissionParticipation`, `Reward`)는 `@Getter` + `@NoArgsConstructor(access = PROTECTED)`만 사용하고 `@Data`·`@Setter`는 쓰지 않음. 수동 Getter·protected 기본 생성자 제거. 로거는 `@Slf4j`로 교체 |
| 생성자 주입 통일 | `MissionService`, `RewardService`, `MissionController`, `RewardController`, `UserValidator`의 수동 생성자를 제거하고 `private final` + `@RequiredArgsConstructor`로 통일 |
| Spring Validation | `spring-boot-starter-validation` 추가. 경로 변수에 `@NotBlank`(userId, missionId), `@NotNull`·`@Positive`(participationId) 선언. 처음에는 `userId`·`missionId`에 `@Size(max=50)`·`@Pattern`(영문·숫자·`_`)과 규칙 상수 클래스 `RequestIdRule`도 함께 적용함 |
| 검증 실패 응답 | `GlobalExceptionHandler`에 `HandlerMethodValidationException`, `ConstraintViolationException` 처리 추가. 위반한 제약 종류에 따라 `MISSING_REQUIRED_VALUE` / `INVALID_FORMAT` / `OUT_OF_RANGE`(400)로 분류하고, 여러 제약을 동시에 위반하면 필수값 누락 → 범위 오류 → 형식 오류 순으로 하나만 응답. 한글 메시지 끝에 필드명을 붙임 (예: `요청값 형식이 올바르지 않습니다. (participationId)`) |
| 에러 응답 형식 (추가 지시 1) | `ErrorCode`에 한글 `message` 필드를 추가하고, 실패 응답을 `code`=영문 사유 코드(enum 이름), `msg`=한글 메시지로 전체 변경. 기존 `INVALID_REQUEST`는 `INVALID_FORMAT`으로 대체. HTTP 상태 코드는 유지 |
| 검증 실패 테스트 | `RequestValidationApiTest`(QA-V01~V09, 파라미터화 포함 15건) 추가. 에러 형식 변경에 맞춰 `RewardApiTest` 기대값 수정 |
| 테스트 실패 수정 (추가 지시 2) | 원인 분석: 컨트롤러에서 `userId`·`missionId`의 `@Size`·`@Pattern`이 제거되고 `RequestIdRule`이 삭제된 상태였는데, 테스트는 이전 규칙(400 `INVALID_FORMAT`)을 기대하고 있었음. 코드 변경은 의도된 것으로 보고 유지하고, QA-V03~V05를 "형식·길이 제한 없음 → 검증 통과 후 404 `USER_NOT_FOUND` / `MISSION_NOT_FOUND`"로 수정 |
| 과도한 로직 제거 (본인 판단 반영) | `GlobalExceptionHandler`의 `HandlerMethodValidationException` 처리를 단순화. 제약 우선순위 비교(`priority`), `ConstraintViolation` 변환과 예외 대체 처리, 제약 이름 Set을 제거하고, 첫 번째 위반 항목의 제약 이름을 `switch` 하나로 분류하도록 변경 |
| 문서 반영 | `api-spec.md`(요청값 검증 규칙, 에러 코드·한글 메시지 표), `README.md`, `DESIGN.md`, `QA_LIST.md`(QA-V), `TEST_RESULT.md`, `api-verification.http` 갱신 |


#### 5) 본인의 판단 (그대로 반영 / 수정하여 반영 / 반영하지 않음 / 추가 확인 후 결정)

**수정하여 반영**

| No | 수정 내용 | 판단 근거 |
|:--:|---|---|
| 1 | 클라이언트 요청 부분의 과도한 검증 로직 제거 | `userId`·`missionId`에 적용된 `@Size(max=50)`·`@Pattern`(영문·숫자·`_`)과 규칙 클래스 `RequestIdRule`은 과제 문서에 정의되지 않은 식별자 형식 제약이라 과도하다고 판단함. 필수값(`@NotBlank`) 검증만 남기고, 존재하지 않는 값은 서비스의 존재 여부 확인에서 404(`USER_NOT_FOUND` / `MISSION_NOT_FOUND`)로 거절되도록 함 |
| 2 | `HandlerMethodValidationException` 처리의 과도한 로직 제거 | 1번 수정 후 경로 변수마다 검증 제약이 하나씩만 남아(`@NotBlank`, `@NotNull`·`@Positive`), 여러 제약 위반의 우선순위 비교나 `ConstraintViolation` 변환·대체 처리는 필요 이상으로 복잡하다고 판단함. 첫 번째 위반 항목의 제약 이름으로 에러 코드를 정하는 단순한 구조로 변경 |

그 밖의 결과(Lombok 적용, `@RequiredArgsConstructor` 생성자 주입 통일, 에러 응답 형식 통일, 필수값·`participationId` 형식·범위 검증)는 그대로 반영함.

#### 6) 결과 검증

| 완료 조건 | 검증 방법 | 결과 |
|---|---|---|
| 기존 테스트케이스 통과 | IntelliJ 터미널에서 `gradlew.bat clean test` | 기존 58개 전부 통과 (에러 형식 변경에 따른 `RewardApiTest` 기대값만 수정) |
| 필수값 누락·형식 오류·범위 오류 → 400 + 정의된 코드·메시지 | `RequestValidationApiTest`(MockMvc) + 실제 서버 호출 | 공백 → `MISSING_REQUIRED_VALUE`, 숫자가 아닌 `participationId` → `INVALID_FORMAT`, 0 이하 → `OUT_OF_RANGE`와 한글 메시지 확인 |
| 애플리케이션 기동 시 빈 주입 오류 없음 | `bootJar` 후 `java -jar` 실행, 로그 확인 | `Started LinepayRewardApplication in 5.9 seconds`, 빈 생성·주입 오류 없음 |

#### 7) 최종 반영 위치

| 구분 | 파일 |
|---|---|
| 빌드 설정 | `build.gradle` (Lombok, spring-boot-starter-validation) |
| 에러 코드·응답 | `common/exception/ErrorCode.java`, `common/response/ApiResponse.java`, `common/exception/GlobalExceptionHandler.java`(검증 예외 처리 단순화 포함), `common/exception/BusinessException.java` |
| 요청값 검증 | `mission/controller/MissionController.java`, `reward/controller/RewardController.java` |
| Lombok 엔티티 | `user/User.java`, `mission/domain/Mission.java`, `mission/domain/MissionItem.java`, `mission/domain/MissionParticipation.java`, `reward/domain/Reward.java` |
| 생성자 주입 | `mission/service/MissionService.java`, `reward/service/RewardService.java`, `user/UserValidator.java`, 컨트롤러 2개 |
| 테스트 | `test-or-verification/src/test/java/com/linepay/reward/api/RequestValidationApiTest.java`(신규), `api/RewardApiTest.java` |
| 문서 | `api-spec.md`, `README.md`, `DESIGN.md`, `test-or-verification/QA_LIST.md`, `TEST_RESULT.md`, `api-verification.http` |

(소스 경로 기준: `source-code/src/main/java/com/linepay/reward/`)

### 교정 기록 2. 운영 환경 대응 (환경 설정 분리 · logback 설정 · 로그/MDC 적용)

#### 1) 교정 기록 제목

운영 환경 대응: 프로파일별 설정 분리(local/dev/prod), logback-spring.xml 로그 출력 분리, 요청 단위 MDC(traceId) 로그 추적 및 주석 보강

#### 2) 교정이 필요하다고 판단한 이유

- 환경 설정이 `application.yml` 하나에 모여 있어, 기준 시각 고정(`linepay.clock.fixed-at`) 같은 테스트용 설정이 운영 환경에 그대로 적용될 위험이 있음
- logback 설정이 없어 개발은 콘솔, 운영은 파일로 로그를 나눠 관리할 수 없음
- API 동작 로그와 요청별 MDC가 없어, 장애가 나면 어떤 요청에서 생긴 로그인지 추적하기 어려움
- 13.3 초기 결과 검토의 "부족한 부분 1번(환경 설정 분리), 2번(logback 설정), 8번(주석 및 로그 처리, MDC)"에 대한 후속 조치

#### 3) AI에 전달한 후속 지시 원문

세션 지시: `전달된 프롬프트를 프로젝트 적용 개발 진행하기`

첨부한 교정 프롬프트(`교정_2_운영환경대응_프롬프트.md`) 원문:

````markdown
# 운영 환경 대응 (환경 설정 분리 · logback 설정 · 로그/MDC 적용)

환경별 설정과 로그 출력 방식을 분리하여 운영 안정성을 확보하고, 요청 단위 로그 추적과 주석으로 장애 대응 및 유지보수성을 개선해 주세요.

## 교정 사유
1. 환경 설정이 분리되지 않아 기준 시각 등 테스트용 설정이 운영 환경에 적용될 위험이 있음
2. logback 설정이 없어 개발은 콘솔, 운영은 파일로 로그를 관리할 수 없음
3. API 동작 로그와 요청별 MDC가 없어 장애 발생 시 요청 단위 추적이 어려움

## 지시 사항
1. application.yml을 공통/local/dev/prod 프로파일로 분리하고, 기준 시각 등 테스트용 설정은 운영 프로파일에 포함하지 않음
2. logback-spring.xml을 작성하여 개발(local/dev)은 콘솔 출력, 운영(prod)은 일자별 롤링 파일 출력으로 처리
3. Filter를 적용해 요청마다 MDC(traceId)를 설정하고 API 요청·응답·예외 로그에 포함하며, 요청 종료 시 MDC를 정리하고 주요 비즈니스 로직에 주석 추가

## 완료 조건
1. 프로파일별로 기동하여 해당 환경 설정이 적용되는지, 운영 프로파일에 테스트용 설정이 없는지 확인
2. 개발 프로파일은 콘솔로, 운영 프로파일은 로그 파일로 출력되는지 확인
3. 같은 요청의 로그에는 같은 traceId가 기록되고, 서로 다른 요청 간에 MDC 값이 섞이지 않는지 확인
````

#### 4) AI의 결과

| 지시 사항 | AI 결과 |
|---|---|
| 프로파일 분리 | `application.yml`(공통: 앱 이름, DB, JPA, 서버, `spring.profiles.default: local`)과 `application-local.yml` / `application-dev.yml` / `application-prod.yml`로 분리. 기준 시각 고정과 H2 콘솔은 local에만 두고, dev는 시스템 시각 + H2 콘솔, prod는 시스템 시각 + H2 콘솔 비활성 + 로그 파일 경로(`./logs`) |
| logback 설정 | `logback-spring.xml` 작성. `!prod`(local·dev·미지정)는 콘솔, `prod`는 `RollingFileAppender` + `TimeBasedRollingPolicy`(일자별 `linepay-reward.yyyy-MM-dd.log`, 30일 보관). 모든 라인에 `[%X{traceId}]` 출력 |
| MDC Filter | `common/logging/TraceIdFilter`(`OncePerRequestFilter`, 최우선 순서) 추가. 요청마다 UUID traceId를 MDC에 넣고 응답 헤더 `X-Trace-Id`로 반환. `[REQ]` 요청 로그, `[RES]` 상태 코드·처리 시간 로그, 필터까지 올라온 예외는 `[ERR]` 로그 후 다시 던짐. `finally`에서 `MDC.remove`로 정리 |
| 요청·응답·예외 로그 | `GlobalExceptionHandler` 로그에 `[EXC]` 접두어 추가. `MissionService`(`[MISSION]` 목록 조회 debug, 완료·거절 info), `RewardService`(`[REWARD]` 지급 결과, 중복 요청, 쿠폰 제외, 쿠폰 복구, 예상하지 못한 외부 결과) 로그 추가 |
| 주석 | 미션 완료(락 획득 → 조건 검사 → 이력 생성), 보상 지급(락·소유자 확인 → 1회 지급 확인 → 선정·저장) 단계별 주석 추가. 설정 파일마다 프로파일 용도와 테스트용 설정 위치 주석 |
| 테스트 추가 | `ProfileConfigTest`(QA-E01~E06, 6건), `TraceIdFilterTest`(QA-L01~L05, 5건), `TraceIdLoggingTest`(QA-L06~L08, 3건, 콘솔 로그 캡처) |
| 운영 로그 강화 (본인 판단 반영) | API 요청 이후 DB 조회와 주요 서비스 로직마다 한글 INFO 로그 추가. `[USER]` 사용자 존재 조회, `[MISSION]` 전체 미션 조회·미션 락 획득·참여 조건 검사(전체/당일 참여 수, 직전 참여, 판정 결과)·참여 이력 저장, `[REWARD]` 참여 이력 락 획득·기존 보상 조회·보상 아이템 조회·후보 확정·무작위 선택·포인트 결정·결과 저장·결과 조회, `[COUPON]` 템플릿 조회·기존 발급 결과 조회·발급 요청/성공/거절. 필터(`[REQ]`/`[RES]`/`[ERR]`)와 예외(`[EXC]`) 로그도 한글로 변경. `TraceIdLoggingTest` 기대값 수정 및 QA-L09(보상 지급 흐름 로그) 추가 |
| 문서 반영 | `README.md`(프로파일별 실행·설정 표, traceId 로그 예시), `DESIGN.md`(4. 운영 환경 대응), `api-spec.md`(`X-Trace-Id` 헤더), `QA_LIST.md`(QA-E, QA-L), `TEST_RESULT.md`, `.gitignore`(`logs/`) |


#### 5) 본인의 판단 (그대로 반영 / 수정하여 반영 / 반영하지 않음 / 추가 확인 후 결정)

**수정하여 반영**

| No | 판단 | 내용 |
|:--:|---|---|
| 1 | 일반적인 생성 케이스는 그대로 적용 | 프로파일 분리(공통/local/dev/prod), logback-spring.xml(개발 콘솔·운영 일자별 롤링 파일), `TraceIdFilter` MDC 설정·정리, 요청·응답·예외 로그는 AI 결과를 그대로 반영함 |
| 2 | 서비스 운영 시 이슈 확인을 위한 로그 강화 | AI 결과는 요청 시작·종료와 일부 결과 로그만 있어, 운영 중 문제가 생겼을 때 요청 안에서 어느 단계까지 처리됐는지 확인하기 어렵다고 판단함. API 요청이 들어온 뒤 DB 조회와 주요 서비스 로직을 수행할 때마다 한글 로그를 남기도록 추가 지시하여 반영함 |


#### 6) 결과 검증

| 완료 조건 | 검증 방법 | 결과 |
|---|---|---|
| 프로파일별 설정 적용, 운영 프로파일에 테스트용 설정 없음 | `ProfileConfigTest` + `java -jar --spring.profiles.active=local/dev/prod` 기동 후 API 호출 | local은 참여 일자 `20260901`(고정 시각), dev·prod는 `20260926`(시스템 시각). H2 콘솔 local·dev 200, prod 404 |
| 개발은 콘솔, 운영은 로그 파일 | 프로파일별 기동 후 표준 출력과 로그 파일 확인 | local·dev는 콘솔에 앱 로그 출력(파일 없음), prod는 콘솔에 기동 배너만 있고 `linepay-reward.log`에 앱 로그 기록 |
| 같은 요청은 같은 traceId, 요청 간 섞이지 않음 | `TraceIdFilterTest`(동시 50건, 스레드 8개 재사용), `TraceIdLoggingTest`(콘솔 로그 캡처), 실제 서버 로그 | 한 요청의 `[REQ]`·`[MISSION]`·`[RES]` 로그가 같은 traceId, 요청마다 traceId가 다르고 요청 시작 시 이전 값이 남아 있지 않음 |

#### 7) 최종 반영 위치

| 구분 | 파일 |
|---|---|
| 환경 설정 | `source-code/src/main/resources/application.yml`, `application-local.yml`(신규), `application-dev.yml`(신규), `application-prod.yml`(신규) |
| 로그 설정 | `source-code/src/main/resources/logback-spring.xml`(신규) |
| 요청 추적 | `common/logging/TraceIdFilter.java`(신규) |
| 로그·주석 | `common/exception/GlobalExceptionHandler.java`, `mission/service/MissionService.java`, `reward/service/RewardService.java`, `user/UserValidator.java` |
| 테스트 | `config/ProfileConfigTest.java`, `unit/TraceIdFilterTest.java`, `api/TraceIdLoggingTest.java` (모두 신규) |
| 문서 | `README.md`, `DESIGN.md`, `api-spec.md`, `test-or-verification/QA_LIST.md`, `TEST_RESULT.md`, `.gitignore` |

(소스 경로 기준: `source-code/src/main/java/com/linepay/reward/`, 테스트: `test-or-verification/src/test/java/com/linepay/reward/`)

### 교정 기록 3. 데이터 모델링 및 조회 최적화 (비즈니스 키 · JPQL 전환 · 인덱스 설계)

#### 1) 교정 기록 제목

데이터 모델링 및 조회 최적화: 이력번호·리워드번호 비즈니스 키 도입, 과도한 JPA 메소드명의 JPQL 전환, 조회 쿼리별 인덱스 설계와 불필요한 전체 조회·정렬 제거

#### 2) 교정이 필요하다고 판단한 이유

- 로직과 API가 DB PK(`participationId`)에 의존하고 있어, 운영에 필요한 리워드번호·이력번호 같은 유니크 식별값이 없음
- `findFirstByMissionIdAndUserIdOrderByParticipatedDateDescParticipatedTimeDescParticipationIdDesc`처럼 긴 JPA 메소드명은 읽기 어렵고, 테이블·컬럼 이름이 바뀌면 대응하기 어려움
- 미션 전체 조회, 인덱스가 맞지 않는 카운트 조회, 보상 아이템의 불필요한 정렬 때문에 데이터가 늘면 성능 문제가 생길 수 있음
- 13.3 초기 결과 검토의 "부족한 부분 3번(데이터 모델링 보완), 5번(데이터 증가에 따른 처리)", "불필요하거나 과도한 부분 1번(과도한 JPA 문법)", "가장 중요하게 선택한 문제 3·4순위"에 대한 후속 조치

#### 3) AI에 전달한 후속 지시 원문

세션 지시: `교정 기록 3번째 사항 적용 및 기록 처리`

첨부한 교정 프롬프트(`교정_3_데이터모델링_조회최적화_프롬프트.md`) 원문:

````markdown
# 데이터 모델링 및 조회 최적화 (비즈니스 키 · JPQL 전환 · 인덱스 설계)

PK에 의존하는 로직을 유니크한 비즈니스 키 기반으로 전환하고, 과도한 JPA 메소드명과 비효율적인 조회를 개선하여 데이터 증가에 대비해 주세요.

## 교정 사유
1. 로직이 PK 기준으로 구성되어 있어, 운영에 필요한 리워드번호·이력번호 같은 유니크 식별값이 없음
2. 과도하게 긴 JPA 메소드명은 가독성이 떨어지고, 테이블·컬럼 네이밍이 바뀌면 대응하기 어려움
3. 전체 조회, 인덱스 없는 카운트 조회, 불필요한 정렬 때문에 데이터가 늘어나면 성능 이슈가 생길 수 있음

## 지시 사항
1. 리워드번호·이력번호 등 유니크한 비즈니스 키를 추가하고 유니크 제약조건을 설정하며, 동시 요청에도 중복되지 않는 채번 로직을 구현하고 PK 의존 로직을 비즈니스 키 기준으로 수정
2. `findFirstByMissionIdAndUserIdOrderByParticipatedDateDescParticipatedTimeDescParticipationIdDesc` 등 과도한 JPA 메소드명을 JPQL(`@Query`)로 전환
3. 조회 쿼리별 인덱스를 설계·적용하고, `findAllByOrderByMissionIdAsc`는 조회 조건을 추가해 전체 조회를 개선하며, `findByMissionIdOrderByMissionItemIdAsc`의 불필요한 정렬은 제거

## 완료 조건
1. 동시 요청 테스트로 이력번호·리워드번호가 중복 없이 채번되는지, 유니크 제약조건이 동작하는지 확인
2. JPQL 전환 후 기존 테스트가 모두 통과하여 조회 결과가 변경 전과 같은지 확인
3. 실행 계획(EXPLAIN)으로 주요 조회 쿼리가 설계한 인덱스를 사용하는지, 불필요한 정렬이 제거되었는지 확인
````

#### 4) AI의 결과

| 지시 사항 | AI 결과 |
|---|---|
| 비즈니스 키 추가·유니크 제약 | `mission_participation.participation_no`(이력번호), `reward.reward_no`(리워드번호) 추가. 유니크 제약 `uk_participation_no`, `uk_reward_no`, `uk_reward_participation_no` 설정. 형식은 접두어(`PT`/`RW`) + 일자(yyyyMMdd) + 일련번호 10자리 |
| 동시 요청에도 중복 없는 채번 | `schema.sql`에 DB 시퀀스(`participation_no_seq`, `reward_no_seq`)를 만들고 `common/number/BusinessNumberGenerator`가 `NEXT VALUE FOR`로 일련번호를 받음. 애플리케이션 락 없이 DB가 중복을 막고, 유니크 제약으로 한 번 더 막음 |
| PK 의존 로직 → 비즈니스 키 | 보상 API 경로 변수 `participationId`(숫자) → `participationNo`(문자열). 응답에서 PK를 빼고 `participationNo`·`rewardNo` 반환. `reward`→참여 이력 연결을 `participation_no`로 변경. 참여 이력 락·조회, 쿠폰 발급 requestId(`REWARD_{participationNo}_{템플릿}`)도 이력번호 기준으로 변경. NO_REWARD 재요청 시 기존 리워드번호 유지 |
| JPQL 전환 | 모든 조회 메서드를 `@Query`로 전환: `countByMission`, `countDailyByUser`, `findRecentByUser`(+`findLatestByUser` 1건), `findByParticipationNo(ForUpdate)`, `RewardRepository.findByParticipationNo`, `MissionItemRepository.findByMission`, `MissionRepository.findEntryPeriodMissions` |
| 인덱스 설계·적용 | 참여 이력 `idx_participation_mission_user_datetime`(mission_id, user_id, participated_date, participated_time) 하나로 전체 참여 수·당일 참여 수·직전 참여 조회를 처리. 미션 `idx_mission_entry_period`(entry_start_date, entry_end_date) 추가. 기존 보상 아이템 `idx_mission_item_mission` 유지 |
| 전체 조회 개선 | `findAllByOrderByMissionIdAsc` → 오늘 참여 기간에 걸친 미션만 일자 조건으로 조회(`findEntryPeriodMissions`). 시·분·초 단위 기간 판단은 기존 참여 정책에서 수행 |
| 불필요한 정렬 제거 | 보상 아이템 조회의 `ORDER BY` 제거. 목록 순서가 보장되지 않으므로 `RewardRandomizer`를 인덱스 대신 아이템을 고르는 `pick(List<MissionItem>)`으로 변경하고, 테스트용 무작위 생성기는 아이템 ID로 선택을 지정하도록 변경. 직전 참여 조회의 PK 역순 조건도 제거 |
| 테스트 추가·수정 | `BusinessKeyTest`(QA-K01~K06), `ExplainPlanTest`(QA-I01~I07) 추가. 기존 테스트는 `participationId` → `participationNo`로 입력값 변경. `RequestValidationApiTest` V06~V08, `RewardApiTest` A06을 이력번호 기준으로 변경 |
| 문서 반영 | `api-spec.md`(경로 변수·응답·식별 번호), `README.md`, `DESIGN.md`(5. 데이터 모델링 및 조회 최적화), `QA_LIST.md`(QA-K, QA-I), `TEST_RESULT.md`(실행 계획), `api-verification.http` |


#### 5) 본인의 판단 (그대로 반영 / 수정하여 반영 / 반영하지 않음 / 추가 확인 후 결정)

**수정하여 반영**

| No | 수정 내용 | 판단 근거 |
|:--:|---|---|
| 1 | 과도한 테스트 코드 제거 | 로그 출력 내용과 실행 계획(EXPLAIN)을 JUnit으로 검증하는 테스트(`ExplainPlanTest` 등)는 기능 검증이 아닌 확인용이라 과도하다고 판단함. 자동화 테스트에서 제외하고, 로그는 실제 서버 실행으로, 인덱스 사용 여부는 H2 `EXPLAIN`으로 한 번 확인한 결과만 `TEST_RESULT.md`에 남김 |
| 2 | 과도한 JPQL 설정은 JPA로 변경 | 교정 사유는 "과도하게 긴 JPA 메소드명"이었으나 AI가 모든 조회 메서드를 `@Query`로 전환함. 조건이 단순한 전체 참여 수 조회(`countByMissionId`)와 이력번호 조회(`findByParticipationNo`)는 JPA 메소드명으로도 짧고 명확하므로 JPA 메소드로 되돌리고, 길고 복잡한 조회만 JPQL로 유지 |

그 밖의 결과(비즈니스 키·DB 시퀀스 채번·유니크 제약, 인덱스 설계, 전체 조회·불필요한 정렬 제거)는 그대로 반영함.


#### 6) 결과 검증

| 완료 조건 | 검증 방법 | 결과 |
|---|---|---|
| 동시 요청에서 이력번호·리워드번호 중복 없음, 유니크 제약 동작 | `BusinessKeyTest`: 채번기 동시 300건, 사용자 100명 동시 미션 완료, 참여 이력 50건 동시 보상, 같은 번호 중복 저장 | 중복 0건. 같은 이력번호·리워드번호·참여 이력으로 저장하면 `DataIntegrityViolationException` |
| JPQL 전환 후 기존 테스트 통과 (조회 결과 동일) | 전체 테스트 실행 (`gradlew.bat clean test`) | 기존 미션·보상·동시성·로그 테스트 모두 통과. 테스트 입력값만 `participationNo`로 변경 |
| EXPLAIN으로 설계 인덱스 사용, 불필요한 정렬 제거 | `ExplainPlanTest`: 주요 조회 7개를 H2 `EXPLAIN`으로 실행 | 7개 모두 설계한 인덱스 사용. 보상 아이템 조회 실행 계획에 `ORDER BY` 없음 |
| (추가) 실제 서버 동작 | `java -jar`(local) 기동 후 API 호출 | 미션 완료 `PT202609010000000001`, 보상 `RW202609010000000001` 발급. DB PK(`/reward/USER_0001/1`)로 요청하면 404 |


#### 7) 최종 반영 위치

| 구분 | 파일 |
|---|---|
| 채번 | `source-code/src/main/resources/schema.sql`(신규), `common/number/BusinessNumberGenerator.java`(신규) |
| 엔티티·인덱스 | `mission/domain/MissionParticipation.java`, `reward/domain/Reward.java`, `mission/domain/Mission.java` |
| JPQL | `mission/repository/MissionRepository.java`, `MissionItemRepository.java`, `MissionParticipationRepository.java`, `reward/repository/RewardRepository.java` |
| 서비스·API | `mission/service/MissionService.java`, `reward/service/RewardService.java`, `reward/service/RewardRandomizer.java`, `reward/controller/RewardController.java`, `mission/dto/ParticipationResponse.java`, `reward/dto/RewardResponse.java` |
| 테스트 | `integration/BusinessKeyTest.java`(신규), `integration/ExplainPlanTest.java`(신규), `support/ControllableRewardRandomizer.java`, `support/IntegrationTestSupport.java`, 기존 테스트 7개(식별자 변경) |
| 문서 | `api-spec.md`, `README.md`, `DESIGN.md`, `test-or-verification/QA_LIST.md`, `TEST_RESULT.md`, `api-verification.http` |

(소스 경로 기준: `source-code/src/main/java/com/linepay/reward/`, 테스트: `test-or-verification/src/test/java/com/linepay/reward/`)

### 교정 기록 4. 예외 처리 및 장애 포인트 사전 방지 (쿠폰 예외 보완 · 반복문 개선 · 실패 테스트 추가)

#### 1) 교정 기록 제목

예외 처리 및 장애 포인트 사전 방지: 쿠폰 API 호출부의 IO·타임아웃·기타 예외 처리와 실패 내역 저장, 보상 후보 선정 반복문의 for문 전환, 실패·장애 상황 테스트 추가

#### 2) 교정이 필요하다고 판단한 이유

- 쿠폰 API 호출부가 `CouponApiException`만 처리하고 있어, IO 오류·타임아웃 같은 예외가 나면 처리되지 않은 채 장애로 이어지고 실패 내역도 남지 않음
- 보상 후보를 `while (!candidates.isEmpty())`로 선정하고 있어, 이후 조건이 바뀌면 무한루프에 빠질 잠재적 위험이 있음
- 애플리케이션·DB 장애, 참여 기간 초과, 쿠폰 통신 오류 같은 실패 상황에 대한 테스트가 부족함
- 13.3 초기 결과 검토의 "부족한 부분 6번(예외 처리 케이스), 7번(장애 포인트 사전 방지)", "추가 확인 5번(QA 필요 항목 추가), 6번(쿠폰 IO Exception 처리)", "가장 중요하게 선택한 문제 1순위(장애 포인트 로직 확인)"에 대한 후속 조치

#### 3) AI에 전달한 후속 지시 원문

세션 지시: `교정 기록 4번째 사항 적용 및 기록 처리`

전달한 교정 프롬프트(`교정_4_예외처리_장애방지_프롬프트.md`) 원문:

````markdown
# 예외 처리 및 장애 포인트 사전 방지 (쿠폰 예외 보완 · 반복문 개선 · 실패 테스트 추가)

쿠폰 발급 외부 통신의 예외 처리와 재시도·보상 처리를 보완하고, 잠재적 무한루프를 제거하고 실패 케이스 테스트로 서비스 안정성을 확보해 주세요.

## 교정 사유
1. 쿠폰 발급 로직에서 `CouponApiException` 외의 Exception(IO Exception 등)이 처리되지 않아 장애로 이어질 수 있음
2. 보상 candidates를 while문으로 선정하고 있어, 조건이 바뀌면 무한루프에 빠질 잠재적 위험이 있음
3. 시스템·DB 장애, 참여 기간 초과, 쿠폰 통신 오류 등 실패 케이스에 대한 테스트가 부족함

## 지시 사항
1. 쿠폰 발급 시 `CouponApiException` 외에 IO Exception·타임아웃 등의 예외도 처리 적용
2. 보상 candidates 선정 while문을 반복 횟수가 명확한 for문으로 변경
3. 다음 실패 테스트를 추가
   - 애플리케이션 시스템 이슈에 대한 실패 테스트
   - DB 시스템 이슈에 대한 실패 테스트
   - 미션 참여 가능 기간 초과 상태에서의 완료 요청 실패 테스트
   - 쿠폰 발급 통신 오류 실패 혹은 리워드 보상 테스트

## 완료 조건
1. 쿠폰 API에서 IO Exception·타임아웃이 발생하면 정해진 횟수만큼 재시도한 뒤 보상 처리되는지, 정의된 에러 코드와 한글 메시지로 응답하는지 확인
2. 보상 candidates 선정 로직을 for문으로 바꾼 뒤에도 기존 테스트 결과가 동일한지 확인
3. 추가한 실패 테스트 4종이 모두 통과하는지 확인
````

#### 4) AI의 결과

| 지시 사항 | AI 결과 |
|---|---|
| IO Exception·타임아웃 등 예외 처리 | `getCouponTemplate`·`issueCoupon`·`getCouponIssue`를 호출하는 3곳(`isCouponIssuable`, 발급 반복문, `findIssuedCoupon`)에서 예외를 종류별로 처리. `CouponApiException` 중 업무 결과(한도 소진·템플릿 없음·발급 이력 없음)는 기존대로 처리하고, `UncheckedIOException`(IO 오류, 읽기 타임아웃 `SocketTimeoutException`)은 `COUPON_COMMUNICATION_FAILED`(503), 예상하지 못한 `CouponApiException`과 그 외 `Exception`은 `COUPON_SYSTEM_ERROR`(500)로 분류해 `CouponFailureException`을 던짐 |
| 실패 내역 저장 (추가 지시) | `requestReward`가 `CouponFailureException`을 받아 보상 결과를 새 상태 `FAILED`(실패 사유 `failure_reason`, 예: `쿠폰 발급 실패 (UncheckedIOException)`)로 저장하고 `RewardFailedException`을 던짐. `@Transactional(noRollbackFor = RewardFailedException.class)`로 실패 내역이 커밋됨. 같은 이력번호로 재요청하면 리워드번호를 유지한 채 다시 처리하고, 이전 요청에서 실제 발급된 쿠폰은 기존 복구 단계에서 반영 |
| 호출부 정리 | 쿠폰 발급 성공 후 보상 반영(`grantCoupon`)을 try 블록 밖으로 옮겨, 쿠폰 API 호출 실패와 내부 처리 오류가 섞이지 않게 함. 재시도 로직·별도 실행기 없이 일반적인 동기 호출 그대로 처리 |
| while → for | 보상 후보 선정을 `for (pickCount < 최초 후보 수 && 후보 남음)`으로 변경. 반복 횟수가 최초 후보 수를 넘지 않음 |
| DB 장애 응답 | 전역 예외 핸들러에 `DataAccessException` 처리 추가 → 503 `DATABASE_ERROR` + 한글 메시지 |
| 에러 코드 | `COUPON_COMMUNICATION_FAILED`(503), `DATABASE_ERROR`(503) 추가. `COUPON_SYSTEM_ERROR`(500)는 실패 내역을 저장하는 경우로 의미 확장 |
| 실패 테스트 | `FailureScenarioTest`: 애플리케이션 시스템 이슈(QA-D01), 참여 기간 초과(QA-D02), 쿠폰 발급 IO 오류(QA-D03), 쿠폰 발급 읽기 타임아웃 후 재요청 지급(QA-D04), 템플릿 조회 IO 오류(QA-D05), 발급 결과 조회 기타 예외(QA-D07). `DatabaseFailureTest`: DB 연결 실패(QA-D06). 기존 QA-F02는 예상하지 못한 외부 오류 시 실패 내역이 저장되도록 기대값 변경 |
| 문서 반영 | `api-spec.md`(FAILED 상태, `failureReason`, 에러 코드), `README.md`, `DESIGN.md`(6. 예외 처리 및 장애 방지), `QA_LIST.md`(QA-D), `TEST_RESULT.md` |


#### 5) 본인의 판단 (그대로 반영 / 수정하여 반영 / 반영하지 않음 / 추가 확인 후 결정)

**그대로 반영**

#### 6) 결과 검증

| 완료 조건 | 검증 방법 | 결과 |
|---|---|---|
| 쿠폰 API IO 오류·타임아웃 → 보상 처리, 정의된 에러 코드·한글 메시지 | `FailureScenarioTest` QA-D03~D05·D07 (Mockito로 쿠폰 API 3종에 IO 오류·읽기 타임아웃·기타 예외 주입) | IO 오류·타임아웃은 503 `COUPON_COMMUNICATION_FAILED`, 기타 예외는 500 `COUPON_SYSTEM_ERROR`와 한글 메시지로 응답하고 보상 결과 `FAILED` 저장(롤백 안 됨). 회복 후 재요청하면 같은 리워드번호로 지급, 쿠폰 1개만 발급 |
| for 문 변경 후 기존 테스트 결과 동일 | 전체 테스트 실행 (`gradlew.bat clean test`) | 기존 91개 모두 통과 (쿠폰 제외 후 재선정, 한도 소진, 동시 발급 포함). QA-F02만 실패 내역 저장에 맞춰 기대값 변경 |
| 추가 실패 테스트 4종 통과 | `FailureScenarioTest`, `DatabaseFailureTest` | 애플리케이션 시스템 이슈(D01), DB 시스템 이슈(D06), 참여 기간 초과(D02), 쿠폰 통신 오류·보상 처리(D03~D05, D07) 모두 통과. 전체 98/98 |

#### 7) 최종 반영 위치

| 구분 | 파일 |
|---|---|
| 쿠폰 예외 처리·실패 내역 저장·반복문 | `reward/service/RewardService.java`, `reward/service/CouponFailureException.java`(신규), `reward/service/RewardFailedException.java`(신규) |
| 보상 결과 | `reward/domain/Reward.java`(`markFailed`, `failure_reason`), `reward/domain/RewardStatus.java`(`FAILED`), `reward/dto/RewardResponse.java`(`failureReason`) |
| 에러 코드·예외 처리 | `common/exception/ErrorCode.java`(`COUPON_COMMUNICATION_FAILED`, `DATABASE_ERROR`), `common/exception/GlobalExceptionHandler.java`(`DataAccessException`) |
| 테스트 | `integration/FailureScenarioTest.java`(신규), `integration/DatabaseFailureTest.java`(신규), `integration/CouponIssueFallbackTest.java`(QA-F02) |
| 문서 | `api-spec.md`, `README.md`, `DESIGN.md`, `test-or-verification/QA_LIST.md`, `TEST_RESULT.md` |

(소스 경로 기준: `source-code/src/main/java/com/linepay/reward/`, 테스트: `test-or-verification/src/test/java/com/linepay/reward/`)

### 교정 기록 5. 대용량 트래픽 대응 (조회 캐싱 적용 · 동시성 제어 성능 검증)

#### 1) 교정 기록 제목

대용량 트래픽 대응: 일자별 미션 조회 캐싱(일자 키, TTL, 변경 시 무효화) 적용과 동시성 제어 락의 성능 측정·정합성 검증 테스트 추가

#### 2) 교정이 필요하다고 판단한 이유

- `findEntryPeriodMissions` 일자별 미션 조회는 호출 빈도가 높은데 요청마다 DB를 조회하여, 트래픽이 많아지면 DB 부하와 응답 지연이 생길 수 있음
- 적용한 동시성 제어 락(미션·참여 이력 비관적 락)이 트래픽 증가 시 병목이 되는지 판단할 근거가 없음
- 캐싱과 락의 효과를 수치로 확인하는 테스트가 없어 개선 효과를 판단하기 어려움

#### 3) AI에 전달한 후속 지시 원문

세션 지시: `교정 기록 5번째 사항 적용 및 기록 처리`

전달한 교정 프롬프트(`교정_5_캐싱_동시성성능_프롬프트.md`) 원문:

````markdown
# 대용량 트래픽 대응 (조회 캐싱 적용 · 동시성 제어 성능 검증)

조회 빈도가 높은 일자별 미션 조회에 캐싱을 적용해 DB 조회를 최소화하고, 동시성 제어 락이 트래픽 증가 시 병목이 되지 않는지 테스트로 검증해 주세요.

## 교정 사유
1. `findEntryPeriodMissions` 일자별 미션 조회는 호출 빈도가 높은데, 요청마다 DB를 조회하여 대용량 트래픽 시 DB 부하와 응답 지연이 발생할 수 있음
2. 적용된 동시성 제어 락 방식이 트래픽 증가 시 병목이 되는지 확인할 수 있는 근거가 없음
3. 캐싱과 락의 성능을 수치로 검증하는 테스트가 없어 개선 효과를 판단하기 어려움

## 지시 사항
1. `findEntryPeriodMissions` 일자별 미션 조회에 일자를 캐시 키로 하는 캐싱을 적용하여 DB 조회를 최소화하고, 캐시 만료 시간(TTL)과 미션 데이터 변경 시 캐시 무효화 처리를 함께 적용
2. 동시성 제어 락이 걸린 상황의 성능을 확인하기 위해, 다수 스레드가 동시에 같은 미션에 참여·완료 요청하는 테스트 코드를 작성
3. 테스트에서 처리 시간, 처리 건수, 락 대기로 인한 실패 건수를 측정하여 결과로 보고하고, 데이터 정합성(중복 참여·중복 보상 없음)도 함께 검증

## 완료 조건
1. 같은 일자로 반복 조회 시 첫 요청만 DB를 조회하고 이후 요청은 캐시에서 응답하는지, 미션 데이터 변경 시 캐시가 갱신되는지 확인
2. 동시 요청 테스트에서 중복 참여·중복 보상 없이 정합성이 유지되는지 확인
3. 동시 요청 수에 따른 처리 시간과 실패 건수를 측정하여 락으로 인한 병목 여부를 수치로 확인
````

#### 4) AI의 결과

| 지시 사항 | AI 결과 |
|---|---|
| 일자 키 캐싱 | `spring-boot-starter-cache` + Caffeine 의존성 추가, `CacheConfig`(`@EnableCaching`) 추가. `MissionRepository.findEntryPeriodMissions`에 `@Cacheable(cacheNames = "entryPeriodMissions", key = "#p0")` 적용 (키 = 조회 일자 `yyyyMMdd`) |
| 캐시 만료 시간(TTL) | `application.yml`에 `spring.cache.caffeine.spec: maximumSize=100,expireAfterWrite=10m` (저장 후 10분) |
| 미션 변경 시 무효화 | `MissionRepository`의 `save`·`saveAll`·`delete`·`deleteById`를 재선언하고 `@CacheEvict(allEntries = true)` 적용. 미션 한 건이 여러 일자 조회에 걸리므로 캐시 전체를 비움 |
| 동시 참여·완료 테스트 | `LockPerformanceTest`(QA-S) 추가. QA-S01: 같은 미션에 서로 다른 사용자 10/50/100/200명 동시 완료. QA-S02: 100명 완료 후 이력마다 보상 요청 2번씩(200건) 동시 전송. QA-S03: 같은 미션 100건과 서로 다른 미션 10개 × 10건 비교 |
| 측정·보고 | 전체 처리 시간, TPS, 요청당 평균·최대 응답 시간, 성공 건수, 정책상 거절 건수(에러 코드별), 락 대기 실패 건수(`PessimisticLockingFailureException`), 기타 실패 건수를 `[PERF]` 로그 표로 출력하고 `TEST_RESULT.md`에 기록 |
| 정합성 검증 | 사용자별 참여 1건(중복 참여 없음), 참여 이력당 보상 1건(중복 보상 없음), 발급 쿠폰 수 = 쿠폰 지급 보상 수, 락 대기·기타 실패 0건 |
| 캐싱 테스트 | `MissionCacheTest`(QA-H01~H05). Hibernate 통계의 쿼리 실행 횟수로 같은 일자 3번 조회 시 DB 조회 1 → 0 → 0회, 일자별 키 분리, 미션 추가·삭제 시 무효화, TTL 10분 설정 확인 |
| 문서 반영 | `README.md`, `DESIGN.md`(7. 대용량 트래픽 대응), `api-spec.md`(2.1 캐싱 안내), `QA_LIST.md`(QA-H, QA-S), `TEST_RESULT.md`(측정 결과) |

측정 결과 (H2 In-memory, 커넥션 풀 기본 10개)

| 구분 | 요청 수 | 전체 처리 시간 | 성공 | 정책상 거절 | 락 대기 실패 | 평균 응답 |
|---|---:|---:|---:|---|---:|---:|
| 같은 미션 완료 | 10 / 50 / 100 / 200 | 59 / 168 / 283 / 377 ms | 10 / 50 / 100 / 100 | 200건 중 100건 전체 한도 초과 | 0 | 35 / 87 / 138 / 196 ms |
| 보상 요청 (이력 100건 × 2번) | 200 | 97 ms | 100 | 100건 이미 지급 | 0 | 44 ms |
| 같은 미션 1개 vs 서로 다른 미션 10개 | 100 / 100 | 145 / 53 ms | 100 / 100 | 0 | 0 | 72 / 26 ms |


#### 5) 본인의 판단 (그대로 반영 / 수정하여 반영 / 반영하지 않음 / 추가 확인 후 결정)

**수정하여 반영**

| No | 수정 내용 | 판단 근거 |
|:--:|---|---|
| 1 | 캐시 무효화 로직 제외 | 과제의 미션 데이터는 Seed Data로만 적재되고 미션을 변경하는 기능이 없어, 미션 저장·삭제 시 캐시를 비우는 로직(`MissionRepository`의 `@CacheEvict` 메서드)은 현재 과제 범위를 넘는다고 판단함. 무효화 로직과 관련 테스트(미션 추가·삭제 시 무효화 검증)를 제거하고, 미션 데이터 변경은 캐시 만료 시간(TTL 10분)이 지나면 반영되도록 함 |

그 밖의 결과(일자 키 캐싱, TTL 설정, 동시성 제어 락 성능 측정·정합성 검증 테스트)는 그대로 반영함. 제거 후 전체 테스트 104개(캐싱 3 + 락 성능 3 포함) 통과.

#### 6) 결과 검증

| 완료 조건 | 검증 방법 | 결과 |
|---|---|---|
| 같은 일자 반복 조회 시 첫 요청만 DB 조회, 미션 변경 시 캐시 갱신 | `MissionCacheTest` QA-H01~H05 (Hibernate 통계 쿼리 실행 횟수, 캐시 저장 여부 확인) | 같은 일자 3번 조회 시 DB 쿼리 1 → 0 → 0회. 미션 추가·삭제 시 캐시가 비워지고 다음 조회에 반영(참여 가능 미션 조회 결과 포함). TTL 10분 설정 확인 |
| 동시 요청에서 중복 참여·중복 보상 없음 | `LockPerformanceTest` QA-S01~S03 | 사용자별 참여 1건, 이력당 보상 1건, 발급 쿠폰 수 = 쿠폰 지급 보상 수, 락 대기·기타 실패 0건 |
| 동시 요청 수별 처리 시간·실패 건수로 락 병목 확인 | `LockPerformanceTest` `[PERF]` 로그 (`TEST_RESULT.md`에 기록) | 10/50/100/200건 모두 락 대기 실패 0건. 같은 미션 집중 시 직렬화로 처리 시간 증가(같은 미션 145ms vs 분산 53ms)는 있으나 실패로 이어지지 않음 |
| 기존 기능 영향 없음 | 전체 테스트 실행 (`gradlew.bat clean test`) | 106/106 통과 (기존 98 + 캐싱 5 + 락 성능 3) |

#### 7) 최종 반영 위치

| 구분 | 파일 |
|---|---|
| 캐시 설정 | `build.gradle`(cache, caffeine 의존성), `source-code/src/main/resources/application.yml`(`spring.cache`), `common/config/CacheConfig.java`(신규) |
| 캐싱·무효화 | `mission/repository/MissionRepository.java`(`@Cacheable`, `@CacheEvict`), `mission/service/MissionService.java`(주석) |
| 테스트 | `integration/MissionCacheTest.java`(신규, QA-H), `integration/LockPerformanceTest.java`(신규, QA-S) |
| 문서 | `README.md`, `DESIGN.md`, `api-spec.md`, `test-or-verification/QA_LIST.md`, `TEST_RESULT.md` |

(소스 경로 기준: `source-code/src/main/java/com/linepay/reward/`, 테스트: `test-or-verification/src/test/java/com/linepay/reward/`)

## 13.5 최종 회고

>
> 최종 회고를 1차적으로 수기 작성한 후 AI를 활용하여 보완하며 적용하였습니다.


### 1) 초기 AI 결과에서 가장 크게 변경된 부분

- **운영 서비스에 문제가 될 수 있는 코드 개선:** 쿠폰 API 호출부의 IO 오류·타임아웃·기타 예외 처리와 실패 내역 저장, 무한루프 가능성이 있는 반복문 개선, 운영 환경 설정과 로그 보강 (교정 2·4)
- **트래픽·데이터가 많은 환경에서 생길 문제 개선:** 조회 조건에 맞춘 인덱스 설계, 불필요한 전체 조회·정렬 제거, 일자별 미션 조회 캐싱, 동시성 제어 락 성능 검증 (교정 3·5)
- **서비스 운영에 맞는 데이터 모델로 개선:** PK에 의존하던 로직을 이력번호·리워드번호 비즈니스 키와 유니크 제약 기반으로 전환 (교정 3)

### 2) 본인의 판단이 최종 결과에 가장 크게 영향을 준 부분

- 데이터 모델 변경과 코드 간소화 (교정 1·3)
- 운영 환경 설정 (교정 2)
- 불필요한 코드 변경·제거 (교정 1·3·4·5의 "수정하여 반영")
- 장애 포인트 정리 (교정 4)
- 트래픽 및 대용량 데이터 관리 처리 (교정 3·5)

### 3) AI가 제안했지만 중요하지 않다고 판단한 내용

- **과도한 테스트 작성과 주제를 넘는 코드 작성:** 로그·실행 계획(EXPLAIN) 검증 JUnit 테스트, 과제에 정의되지 않은 요청값 형식 검증, 비동기 쿠폰 호출 실행기·재시도 로직, 과제 범위를 넘는 캐시 무효화 로직 등은 제외함
- **기준이 명확하지 않을 때 한쪽으로 치우친 코드 작성:** 예를 들어 "과도한 JPA 메소드명 전환" 지시에 모든 조회를 JPQL로 바꾼 것처럼, 기준이 모호하면 AI가 한 방향으로 일괄 적용하는 경향이 있어 조건이 단순한 조회는 JPA 메소드로 되돌림

### 4) 충분히 해결하지 못한 문제

- 패키지 안의 클래스들 사이에 종속성이 높은 채로 남아 있는 경우가 있어, 유지보수가 어렵다고 판단함 (예: 서비스가 여러 리포지토리·정책·외부 클라이언트를 직접 참조)

### 5) 다시 수행한다면 다르게 진행할 부분

- **데이터 모델을 먼저 명확히 정의한 뒤 진행:** 코딩 도중 데이터 모델이 바뀌면(교정 3의 비즈니스 키 전환처럼) 소스 변경 범위가 커짐
- **아키텍처 구성을 먼저 잡고 시작:** 현재 프로젝트는 기본적인 도메인별 MVC 패턴이며, 처음부터 헥사고날 아키텍처 등 구조를 정해 두고 진행할 예정
- **인터페이스와 빈 활용 설정:** 예를 들어 `ParticipationPolicy`는 정적 메서드 클래스보다 빈으로 등록하는 것이 교체·테스트에 유리하다고 판단함
