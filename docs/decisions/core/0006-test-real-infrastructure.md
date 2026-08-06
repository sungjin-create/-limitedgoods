# ADR-0006: PostgreSQL·Redis 고유 동작을 Testcontainers로 검증한다

- 상태: accepted

## 문제

Core는 PostgreSQL 조건부 update, `ON CONFLICT`, 비관적 잠금, Flyway와 Redis Lua·TTL에 의존합니다. H2와 mock만으로는 동일한 동작을 보장할 수 없습니다.

## 결정

DB가 필요 없는 검증은 기본 `test`에서 실행하고, 실제 PostgreSQL 16과 Redis 7 검증은 `integrationTest` source set에서 Testcontainers로 실행합니다.

## 검증 범위

- Flyway `V1` Core baseline과 `V2` refresh token migration
- 재고, 구매 제한, checkout token, 결제 key 경합
- 주문 만료의 재고·예약 복구
- Redis 대기열 Lua, TTL, token claim·release·consume

Docker가 없는 환경에서는 통합 테스트를 통과한 것으로 간주하지 않습니다.
