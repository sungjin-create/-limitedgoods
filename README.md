# Limited Goods Core

한정 상품 주문에서 발생하는 대기열, 재고 경합, 주문·결제 멱등성과 장애 상태를 학습하고 검증하는 Spring Boot 프로젝트입니다.

## Core 범위

- JWT 회원가입·로그인
- 일반/한정 상품과 판매 시작 시각
- Redis 상품별 대기열과 admission token
- PostgreSQL 조건부 재고 차감
- 사용자·상품별 누적 구매 제한
- `checkoutToken` 기반 주문 생성 멱등성
- 5분 주문 예약과 미결제 주문 만료
- `Idempotency-Key` 기반 결제 시도
- PG 승인과 내부 주문 확정 분리
- 결제 거절·timeout·결과 불명 상태
- 정상 전액 환불과 재고 복구
- 관리자 상품 등록, 재고 조정, 타입·판매 시작 설정
- PostgreSQL·Redis Testcontainers 및 k6 시나리오

Monitoring, Kafka/Outbox, 이메일·분석 projection, 환불 reconciliation과 관리자 환불 운영 기능은 Core에 포함하지 않습니다. 상세 범위는 [PROJECT_SCOPE](./PROJECT_SCOPE.md)를 참고하세요.

## 기술 스택

- Java 17, Spring Boot 3.5.6
- Spring Web, Security, Validation, Data JPA/JDBC, Retry
- PostgreSQL 16, Flyway
- Spring Data Redis
- JUnit 5, Testcontainers, k6

## 로컬 실행

Java 17과 Docker가 필요합니다. 예제 파일을 복사한 뒤 로컬 값을 설정합니다.

```powershell
Copy-Item .env.example .env
```

```powershell
$env:POSTGRES_DB = "limitedgoods_flyway"
docker compose up -d
```

| 대상 | 기본 주소 |
| --- | --- |
| API | `http://localhost:8080` |
| Health | `http://localhost:9091/actuator/health` |
| PostgreSQL | `localhost:5432`, DB `limitedgoods_core` |
| Redis | `localhost:6379` |

Flyway는 빈 DB에 `V1__core_baseline.sql`을 적용합니다. 과거 migration이 적용된 개발 DB는 기존 볼륨을 보존할 필요가 없을 때만 새 볼륨으로 초기화합니다.

## 테스트

```powershell
.\gradlew.bat test
.\gradlew.bat integrationTest
```

`integrationTest`는 Docker에서 PostgreSQL 16과 Redis 7을 실행합니다. Docker가 없거나 중지돼 있으면 통과한 것으로 간주하지 않습니다.

부하 테스트는 [k6 실행 문서](./k6/README.md)를 참고하세요.

## 문서

- [문서 인덱스](./docs/README.md)
- [아키텍처](./docs/architecture.md)
- [API](./docs/api-reference.md)
- [도메인 정책](./docs/policies/README.md)
- [설계 결정](./docs/decisions/README.md)

## 현재 제한과 후속 작업

- 통계는 내부 Worker가 갱신하므로 비동기 처리 지연이 존재합니다.
- 관리자 계정 bootstrap 기능이 없어 로컬 DB에서 역할을 변경해야 합니다.
- 대기 예상 시간은 `position × 2초`인 단순 추정값입니다.
- 대기열 이탈자의 Sorted Set entry를 자동 정리하는 정책이 아직 없습니다.
- 상품 검색은 일반 목록과 달리 비공개·보관 상태를 거르지 않습니다.
- 실제 PG/메일 사업자 연동 시 timeout, idempotency, webhook 검증 정책을 추가로 검증해야 합니다.
- 운영용 secret은 소스 기본값이 아니라 secret manager 또는 배포 환경 변수로 주입해야 합니다.
