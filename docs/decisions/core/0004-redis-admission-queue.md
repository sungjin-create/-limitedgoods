# ADR-0004: 한정 상품 앞에 Redis admission queue를 둔다

- 상태: accepted

## 문제

모든 사용자를 동시에 주문 transaction으로 보내면 재고가 적어도 DB connection과 잠금 경쟁이 크게 증가합니다.

## 결정

상품별 Redis Sorted Set으로 순번을 유지하고 active window 안의 사용자에게 5분 admission token을 발급합니다. token은 주문 시작 시 claim하고 성공 시 소비합니다.

## 결과

5,000명이 10초 동안 재고 100개인 상품에 접근한 대표 실험에서 주문 API와 DB transaction 진입 후보가 Direct 5,000건에서 Queue 104건으로 97.92% 감소했습니다. 두 모드 모두 주문 100건을 생성했고 예상하지 않은 오류는 없었습니다.

Queue는 최종 정합성 수단이 아닙니다. 재고 차감과 구매 제한은 PostgreSQL이 최종 결정하며, 대기열 통과도 구매 성공을 보장하지 않습니다. Queue는 트래픽이 집중되는 LIMITED 상품에만 적용하는 DB 보호 장치로 사용합니다.

대기와 상태 조회가 추가되므로 사용자 전체 흐름의 응답시간은 Direct보다 길어질 수 있습니다. 또한 Redis 장애 시 한정 상품 주문은 정합성과 우회 방지를 위해 fail-close합니다.

측정 조건과 반복 결과는 [Core 대기열·동시성 검증 결과](../../report/core-test-result.md)에 기록합니다.
