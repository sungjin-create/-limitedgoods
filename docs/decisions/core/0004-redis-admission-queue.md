# ADR-0004: 한정 상품 앞에 Redis admission queue를 둔다

- 상태: accepted

## 문제

모든 사용자를 동시에 주문 transaction으로 보내면 재고가 적어도 DB connection과 잠금 경쟁이 크게 증가합니다.

## 결정

상품별 Redis Sorted Set으로 순번을 유지하고 active window 안의 사용자에게 5분 admission token을 발급합니다. token은 주문 시작 시 claim하고 성공 시 소비합니다.

## 결과

DB로 진입하는 동시 요청을 제한할 수 있습니다. Redis 장애 시 한정 상품 주문은 fail-close하며, 대기열 통과가 판매 성공을 보장하지는 않습니다.
