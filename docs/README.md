# Limited Goods 문서

문서는 핵심 주제와 구현 참고 자료를 분리합니다.

## 먼저 읽을 문서

1. [프로젝트 범위](../PROJECT_SCOPE.md)
2. [아키텍처](./architecture.md)
3. [Core 대기열·동시성 검증 결과](./report/core-test-result.md)
4. [핵심 테스트 계획](./report/core-test-completion-plan.md)
5. [k6 대기열 비교 가이드](../k6/README.md)

## 설계 근거

- [PostgreSQL을 최종 기준으로 사용](./decisions/core/0001-postgresql-source-of-truth.md)
- [재고와 구매 제한 원자 예약](./decisions/core/0002-atomic-order-reservation.md)
- [주문 생성 멱등성](./decisions/core/0003-idempotent-order-creation.md)
- [Redis admission queue](./decisions/core/0004-redis-admission-queue.md)
- [실제 PostgreSQL·Redis 테스트](./decisions/core/0006-test-real-infrastructure.md)

## 구현 참고

- [API](./api-reference.md)
- [도메인 정책](./policies/README.md)
- [나머지 설계 결정](./decisions/README.md)

결제·환불·인증 문서는 현재 구현을 이해하기 위한 참고 자료이며 Core 성과의 중심 문서는 아닙니다.
