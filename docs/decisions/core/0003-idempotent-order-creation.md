# ADR-0003: checkout token과 fingerprint로 주문 생성을 멱등하게 처리

- 상태: accepted

## 문제

클라이언트가 timeout 뒤 주문을 재요청하면 주문과 재고 예약이 중복될 수 있습니다.

## 결정

사용자와 `checkoutToken` 조합에 unique 제약을 두고, 정렬된 상품·수량으로 fingerprint를 저장합니다. token과 내용이 같으면 기존 주문을 반환하고 내용이 다르면 거부합니다.

## 결과

재시도는 안전해지지만 클라이언트가 논리적으로 같은 주문에 같은 token을 유지해야 합니다. 한정 상품 token claim 결과가 불명확할 때도 DB 주문을 먼저 확인합니다.
