# AI 활용 기록 (AI_USAGE.md)

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

진행 중 추가 지시

| 순서 | 상황 | 지시 |
|:--:|---|---|
| 1 | AI 질문: "영문 코드 + 한글 메시지" 지시가 최초 프롬프트 4.2의 응답 규격(`code="E404"`, `msg="MISSION_NOT_FOUND"`)과 달라 에러 응답 형식을 어떻게 맞출지 확인 요청 | 선택: **전체 에러 통일** (모든 에러를 `code`=영문 사유 코드, `msg`=한글 메시지로 변경) |
| 2 | 적용 이후 `RequestValidationApiTest` 실패 | `RequestValidationApiTest 로직 테스트가 실패하는데 확인 후 수정` |

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

AI가 진행 중 발견해 보고한 사항

- 교정 지시(영문 코드 + 한글 메시지)가 최초 프롬프트 4.2의 응답 예시와 충돌 → 임의로 정하지 않고 선택지를 제시해 확인 요청
- `USER_0001;DROP` 테스트가 200으로 통과: Spring MVC가 `;` 뒤를 matrix variable로 보고 잘라낸 뒤 검증하는 프레임워크 동작. 테스트 값을 교체하고 `api-spec.md`에 동작을 기록
- Lombok·Validation 추가는 최초 프롬프트의 "그밖 의존성은 임의로 추가하지 않는다"와 달라지는 변경임을 알림

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

테스트 실행 이력

| 회차 | 결과 | 비고 |
|:--:|---|---|
| 1 | 72/73 통과 | `USER_0001;DROP` 케이스 실패 (matrix variable 동작) → 테스트 값 교체 |
| 2 | 73/73 통과 | 실제 서버 기동·API 호출 확인 |
| 3 | 66/73 통과 | `@Size`·`@Pattern` 제거 후 QA-V03~V05 7건 실패 |
| 4 | 73/73 통과 | 테스트를 현재 규칙에 맞춰 수정. 전체 실행과 `RequestValidationApiTest` 단독 실행 모두 통과 |
| 5 | 73/73 통과 | `HandlerMethodValidationException` 처리 단순화 후 `gradlew.bat clean test bootJar` 재실행. 검증 응답 코드·메시지 변화 없음 |

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

## 13.5 최종 회고

> 이번 요청에서는 제외 (후속 작성 예정)
