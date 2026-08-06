# ADR-0001: PostgreSQL을 주문·재고의 최종 기준으로 사용

- 상태: accepted

## 문제

Redis와 PostgreSQL에 판매 상태가 함께 존재하면 장애나 지연 중 값이 달라질 수 있습니다.

## 결정

주문, 재고, 구매 제한, 결제·환불 시도의 최종 성공 여부는 PostgreSQL transaction과 제약 조건으로 결정합니다. Redis의 대기열·품절 값은 진입 제어와 빠른 거절을 위한 보조 상태입니다.

## 결과

Redis가 오래된 값을 반환해도 초과 판매를 확정하지 않습니다. 대신 모든 주문이 PostgreSQL 검증을 거치므로 DB connection과 잠금 구간을 짧게 유지해야 합니다.
