# Core 설계 결정

기술 선택 자체보다 동시성·멱등성·복구 경계를 설명하는 결정만 유지합니다.

| ID | 결정 | 상태 |
| --- | --- | --- |
| [ADR-0001](./core/0001-postgresql-source-of-truth.md) | PostgreSQL을 주문·재고의 최종 기준으로 사용 | accepted |
| [ADR-0002](./core/0002-atomic-order-reservation.md) | 재고와 구매 제한을 주문 transaction에서 원자적으로 예약 | accepted |
| [ADR-0003](./core/0003-idempotent-order-creation.md) | checkout token과 fingerprint로 주문 생성 멱등성 보장 | accepted |
| [ADR-0004](./core/0004-redis-admission-queue.md) | 한정 상품 앞에 Redis admission queue 배치 | accepted |
| [ADR-0005](./core/0005-separate-payment-approval.md) | PG 승인과 내부 주문 확정 분리 | accepted |
| [ADR-0006](./core/0006-test-real-infrastructure.md) | PostgreSQL·Redis 고유 동작을 Testcontainers로 검증 | accepted |
| [ADR-0007](./core/0007-rotating-refresh-token.md) | 회전형 Refresh Token을 hash로 저장 | accepted |

Monitoring, Kafka/Outbox, 환불 reconciliation은 Core 결정 목록에 포함하지 않습니다.
