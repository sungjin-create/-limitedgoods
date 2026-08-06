# ADR-0005: PG 승인과 내부 주문 확정을 분리한다

- 상태: accepted

## 문제

외부 PG 호출과 로컬 DB transaction을 하나로 묶을 수 없습니다. PG 승인 뒤 DB 확정이 실패했을 때 PG를 다시 호출하면 중복 결제가 발생할 수 있습니다.

## 결정

결제 시도를 영속화하고 PG 승인을 `PAYMENT_APPROVED`로 먼저 기록한 뒤 별도 transaction에서 `PAID`를 확정합니다. 결제 key와 fingerprint로 같은 시도를 재사용하며 timeout은 `UNKNOWN`으로 구분합니다.

## 결과

내부 확정 실패는 PG 재호출 없이 복구할 수 있습니다. 반면 외부 결과 조회 adapter가 정확한 멱등성과 상태 조회를 제공해야 합니다.
