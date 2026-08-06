# 대기열 정책

대기열은 `LIMITED` 상품에만 적용합니다. PostgreSQL 주문 transaction이 최종 재고와 판매 가능 여부를 다시 검증합니다.

## 진입과 순번

- 상품별 Redis Sorted Set에 사용자 ID와 최초 진입 시각을 저장합니다.
- `ZADD NX`를 사용하므로 재진입해도 기존 순서를 유지합니다.
- 기본 active window는 40명이며 환경 변수로 조정할 수 있습니다.
- 상품 대기열 상태가 없거나 `OPEN`이 아니면 fail-close합니다.
- 예상 대기 시간은 `대기 순번 × 2초`인 표시값입니다.

## heartbeat와 정리

클라이언트는 2~3초 간격으로 상태를 조회하고 대기 중 heartbeat를 전송합니다. 30초 이상 활동이 없는 사용자는 cleanup 대상이며, 주문에서 token을 claim한 사용자는 제외합니다.

## admission token

- 기본 TTL은 300초입니다.
- 사용자와 상품에 묶여 다른 요청에 재사용할 수 없습니다.
- 주문 시작 시 `checkoutToken`과 함께 claim합니다.
- 주문 성공 시 소비하고, 확정된 실패 시 남은 TTL로 복구합니다.
- 결과가 불명확하면 같은 `checkoutToken`의 DB 주문을 먼저 확인합니다.

Redis 장애 시 주문 rate limit은 fail-open이지만 한정 상품 대기열과 admission token 검증은 우회하지 않습니다.
