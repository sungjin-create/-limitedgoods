# 이벤트 전달 모드

현재 백엔드에서 실제로 동작하는 Outbox 처리 경로는 `internal-worker` 프로필입니다.
Kafka 외부 소비자 경로는 설계와 PoC 코드만 있고 기본 실행 경로가 아닙니다.

claim, lease, 재시도와 이메일 발송의 상세 순서는 [Worker 타임라인](./worker-timeline.md)을
참고합니다.

## 내부 Worker 모드

```powershell
$env:SPRING_PROFILES_ACTIVE = "internal-worker"
.\gradlew.bat bootRun
```

내부 Worker가 다음 이벤트를 처리합니다.

| 이벤트 | 통계 | 이메일 delivery |
| --- | --- | --- |
| `ORDER_CREATED` | 주문 생성 퍼널 | 없음 |
| `ORDER_PAID` | 매출·상품·결제 퍼널 | 결제 완료 |
| `PAYMENT_FAILED` | 결제 실패 퍼널 | 없음 |
| `ORDER_EXPIRED` | 만료 퍼널 | 없음; template type만 정의 |
| `ORDER_CANCELED` | 환불·상품·환불 퍼널 | 없음; template type만 정의 |

Outbox와 이메일 Worker는 PostgreSQL claim token, processing lease,
`FOR UPDATE SKIP LOCKED` 방식으로 여러 인스턴스의 경합을 제어합니다. 이벤트 소비는
`internal_processed_event`로 멱등 처리합니다.

내부 프로필의 Outbox 기본 처리 주기는 30초이며 최대 시도·batch·lease는 설정으로
조절할 수 있습니다. 실패한 이메일 delivery는 backoff 후 재시도하고 한도를 넘으면
`DEAD`가 됩니다. 관리자는 DEAD 목록을 조회하고 다시 대기 상태로 보낼 수 있습니다.

## SMTP 설정

메일 발송 Worker는 기본적으로 꺼져 있습니다. 다음 값을 secret 저장소나 로컬 `.env`에
넣고 `MAIL_ENABLED=true`로 켭니다.

```properties
MAIL_ENABLED=true
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=smtp-user
MAIL_PASSWORD=smtp-password
MAIL_FROM=no-reply@example.com
```

추가 조절값:

- `MAIL_CONNECTION_TIMEOUT_MS` (기본 10000)
- `MAIL_READ_TIMEOUT_MS` (기본 10000)
- `MAIL_WRITE_TIMEOUT_MS` (기본 10000)
- `MAIL_INFRASTRUCTURE_BACKOFF_MS` (기본 300000)

SMTP 수신 성공과 DB의 `SENT` 갱신은 한 transaction으로 묶을 수 없습니다. SMTP가
성공한 직후 프로세스가 중단되면 lease 만료 후 중복 발송될 수 있습니다. 엄격한 중복
억제가 필요하면 `eventId` 기반 멱등 key를 지원하는 provider API가 필요합니다.

## Kafka 외부 소비자 PoC

`limitedgoods-event-platform`에는 알림과 통계 consumer가 있지만 현재 다음 이유로
백엔드와 자동 연결되지 않습니다.

- 백엔드 `KafkaOutboxPublisher` 소스가 전부 주석 처리돼 있습니다.
- `application.yml`의 Kafka bootstrap 설정도 주석 처리돼 있습니다.
- `docker-compose.yml`의 Kafka와 Kafka UI 서비스도 주석 처리돼 있습니다.
- 외부 consumer는 `ORDER_PAID`만 처리합니다.
- 외부 event contract와 백엔드 payload가 완전히 일치하지 않습니다.

PoC를 다시 활성화할 때는 Publisher, Kafka 설정과 Compose 서비스를 함께 복구하고,
같은 환경에서 내부 Worker와 외부 Publisher가 동일 Outbox 행을 경쟁하지 않도록 실행
모드를 명시적으로 분리해야 합니다.

정식 외부 모드로 전환하기 전 필요한 작업:

1. event contract 타입·버전 통일
2. `ORDER_CANCELED` 등 보상 이벤트 구현
3. retry topic 또는 DLT와 consumer lag 관측
4. analytics 조회 API와 projection 재구축 절차
5. 통합 테스트로 발행·소비·멱등성 검증

## Schema 변경

현재 schema 기준은 `src/main/resources/db/migration`의 Flyway migration입니다.
[`sql/internal-email-delivery-v2.sql`](./sql/internal-email-delivery-v2.sql)은 Flyway 도입
전 또는 별도 기존 DB를 수동 보정하기 위한 참고 SQL이며 신규 환경의 기본 설치 절차가
아닙니다.
