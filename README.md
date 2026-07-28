# Limited Goods Backend

한정 상품 주문의 동시성 제어와 운영 안정성을 중심으로 만든 Spring Boot 백엔드입니다. 상품·장바구니 CRUD보다 주문 승인 경로, 재고 보존, 결제 멱등성, 만료 복구, Outbox 후처리와 관측 가능성에 초점을 둡니다.

## 구현 범위

- JWT 회원가입·로그인과 관리자 권한 분리
- 일반/한정 상품, 판매 일정과 상태 전이
- Redis Sorted Set 기반 한정 상품 대기열과 5분 입장 토큰
- 사용자·상품별 주문 요청 제한과 품절 캐시
- `checkoutToken` 기반 주문 생성 멱등성
- PostgreSQL 조건부 재고 차감과 동시 주문 방어
- 5분 주문 예약, 60초 주기 만료 처리와 재고 복구
- 결제 시도 영속화, `Idempotency-Key`와 fingerprint 검증
- 결제 승인과 내부 주문 확정 분리
- 전체 환불, 환불 실패 재시도와 재고 복구
- Transactional Outbox와 내부 멱등 소비
- 일별 매출·환불·상품 수량·주문 퍼널 projection
- 관리자 대시보드, 7일/30일 상품 순매출 순위
- Actuator, Prometheus, Grafana와 비즈니스 커스텀 메트릭
- 이메일 재시도·DEAD 관리 API와 k6 대기열 부하 시나리오

## 기술 스택

- Java 17, Spring Boot 3.5.6
- Spring Web, Security, Validation
- Spring Data JPA/JDBC, PostgreSQL 16, Flyway
- Spring Data Redis, Redisson
- Micrometer, Actuator, Prometheus, Grafana
- JUnit 5, Spring Boot Test
- Docker Compose, k6

## 아키텍처

```text
HTTP API
  ├─ 사용자·상품·장바구니
  ├─ 대기열·주문·결제·환불
  └─ 관리자 상품·주문·통계·모니터링
          │
          ├─ PostgreSQL
          │    ├─ 도메인 원본 데이터
          │    ├─ payment_attempt
          │    ├─ outbox_event
          │    └─ analytics projection
          │
          ├─ Redis
          │    ├─ 대기열·입장 토큰
          │    ├─ 요청 제한
          │    └─ 품절·결제 멱등 보조 상태
          │
          └─ internal-worker
               ├─ 통계 projection 갱신
               └─ 이메일 delivery 생성·발송
```

자세한 설명은 [아키텍처 문서](./docs/architecture.md)를 참고합니다.

## 로컬 실행

### 요구 사항

- Java 17
- Docker
- Windows에서는 Gradle Wrapper와 PowerShell 사용 가능

### 인프라

전체 로컬 인프라를 실행합니다.

```powershell
$env:POSTGRES_DB = "limitedgoods_flyway"
docker compose up -d
```

Compose 기본 DB 이름은 `limitedgoods`이지만 애플리케이션 기본 JDBC URL은
`limitedgoods_flyway`를 사용합니다. 위처럼 DB 이름을 맞추거나
`SPRING_DATASOURCE_URL`을 `jdbc:postgresql://localhost:5432/limitedgoods`로 지정합니다.

서비스와 포트:

| 서비스 | 기본 포트 |
| --- | ---: |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Prometheus | 9090 |
| Grafana | 3000 |

### 애플리케이션

현재 권장 실행 방식은 내부 Outbox Worker 모드입니다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "internal-worker"
.\gradlew.bat bootRun
```

기본 API 주소는 `http://localhost:8080`입니다. Flyway가 애플리케이션 시작 시 schema migration을 검증하고 적용합니다.

> `internal-worker` 없이 실행하면 Outbox를 처리하는 Worker가 동작하지 않습니다. 자세한 내용은 [이벤트 처리 모드](./docs/event-delivery-modes.md)를 참고합니다.

## 관리자 계정

회원가입은 항상 `USER` 역할을 생성합니다. 로컬에서 관리자 화면을 확인하려면 회원가입 후 DB 역할을 변경하고 다시 로그인합니다.

```sql
UPDATE users
SET role = 'ADMIN'
WHERE email = 'admin@example.com';
```

관리자 API는 `/api/admin/**`이며 JWT의 `ADMIN` 역할이 필요합니다.

## 주요 설정

`application.yml`은 `.env` 파일을 선택적으로 읽습니다. 저장소의 기본값은 로컬 개발용이므로 운영 환경에서는 반드시 환경 변수로 덮어씁니다.

| 환경 변수 | 용도 | 기본값 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/limitedgoods_flyway` |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자 | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 | 로컬 개발값 |
| `SPRING_DATA_REDIS_HOST` | Redis 호스트 | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Redis 포트 | `6379` |
| `PROMETHEUS_BASE_URL` | 관리자 모니터링 조회 주소 | `http://localhost:9090` |
| `LOKI_BASE_URL` | Loki 조회 주소 | `http://localhost:3100` |
| `APP_ENV` | metric 공통 태그 | `local` |
| `LOG_PATH` | 로그 파일 경로 | logback 기본값 |

내부 메일 발송 관련 변수는 [이벤트 처리 모드](./docs/event-delivery-modes.md)에 정리돼 있습니다.

## 테스트

```powershell
.\gradlew.bat test
```

현재 테스트는 다음 영역을 다룹니다.

- 주문·결제·상품 도메인의 상태 전이와 예외 조건
- 주문 생성 전제조건, 대기열 입장권, checkout 멱등성과 재고 동시성
- 결제 승인·거절·타임아웃·재조정과 결제 멱등 재생
- 내부 Outbox Worker의 claim 처리, 재시도와 소유권 상실
- 이메일 delivery 상태, 재시도 Worker와 provider circuit
- 관리자 통계 기간 검증, 이벤트 중복 방어, 매출·환불 projection 반영

PostgreSQL·Redis처럼 실제 외부 인프라와의 호환성은 별도의 통합 환경에서 추가 검증해야 합니다.

부하 테스트는 `k6/k6-order-test.js`에 있습니다. 스크립트의 `PRODUCT_ID`와 재고를 환경에 맞게 조정한 뒤 실행합니다.

```powershell
k6 run .\k6\k6-order-test.js
```

## 관측

- Health: `GET /actuator/health`
- Prometheus scrape: `GET /actuator/prometheus`
- Grafana: `http://localhost:3000`
- 관리자 통합 모니터링: `GET /api/admin/backoffice/monitoring/overview`

커스텀 메트릭:

- `limitedgoods.order.create`
- `limitedgoods.order.expired`
- `limitedgoods.payment`
- `limitedgoods.payment.revenue`

Prometheus는 Docker 컨테이너에서 `host.docker.internal:8080`의 백엔드를 15초 간격으로 수집합니다.

## 문서

- [문서 인덱스](./docs/README.md)
- [아키텍처](./docs/architecture.md)
- [API 요약](./docs/api-reference.md)
- [통계와 모니터링](./docs/analytics-and-monitoring.md)
- [이벤트 처리 모드](./docs/event-delivery-modes.md)
- [Worker 타임라인](./docs/worker-timeline.md)
- [도메인 정책](./docs/policies/README.md)

## 현재 제한과 후속 작업

- 통계는 내부 Worker가 갱신하므로 비동기 처리 지연이 존재합니다.
- 일별 환불 수량 갱신 native SQL은 현재 column/value 구문이 맞지 않아 수정이 필요합니다.
- 관리자 계정 bootstrap 기능이 없어 로컬 DB에서 역할을 변경해야 합니다.
- 대기 예상 시간은 `position × 2초`인 단순 추정값입니다.
- 대기열 이탈자의 Sorted Set entry를 자동 정리하는 정책이 아직 없습니다.
- 상품 검색은 일반 목록과 달리 비공개·보관 상태를 거르지 않습니다.
- 실제 PG/메일 사업자 연동 시 timeout, idempotency, webhook 검증 정책을 추가로 검증해야 합니다.
- 운영용 secret은 소스 기본값이 아니라 secret manager 또는 배포 환경 변수로 주입해야 합니다.
