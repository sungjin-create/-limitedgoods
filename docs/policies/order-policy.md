# 주문 정책

## 생성 검증

- 로그인 사용자만 주문할 수 있습니다.
- `checkoutToken`은 필수이며 최대 255자입니다.
- 주문 항목은 1~50개이고 상품 ID와 수량은 양수입니다.
- 중복 상품 항목은 허용하지 않습니다.
- 한정 상품은 단독 항목과 admission token이 필요합니다.
- 사용자·상품별 주문 요청 제한은 10초에 3회입니다.

## 멱등성

사용자와 `checkoutToken` 조합을 DB unique 제약으로 보호합니다. 상품 ID순으로 정렬한 상품·수량 fingerprint가 같으면 기존 주문을 반환하고, 다르면 `IDEMPOTENCY_KEY_REUSED` 오류를 반환합니다.

새 주문을 만들 때 기존 `CREATED`, `PAYMENT_FAILED` 주문은 만료시켜 예약을 복구합니다. `PAYMENT_PENDING`, `PAYMENT_APPROVED` 주문이 있으면 새 주문을 거부합니다.

## 재고와 구매 제한 예약

상품별 조건부 update가 상태, 판매 시각, 재고를 한 번에 확인해 차감합니다. 사용자·상품별 `reserved_quantity + paid_quantity`도 PostgreSQL `ON CONFLICT ... WHERE`로 제한합니다. 하나라도 실패하면 주문 transaction 전체가 rollback됩니다.

## 만료

주문 예약 시간은 기본 5분입니다. 60초 주기의 스케줄러가 기한이 지난 `CREATED`, `PAYMENT_FAILED` 주문을 `EXPIRED`로 변경하고 재고와 구매 제한 예약을 복구합니다. 같은 주문을 다시 만료해도 복구는 한 번만 일어납니다.

## 주요 상태

| 상태 | 의미 | 다음 상태 |
| --- | --- | --- |
| `CREATED` | 주문과 재고 예약 완료 | `PAYMENT_PENDING`, `EXPIRED` |
| `PAYMENT_PENDING` | PG 결과 대기 | `PAYMENT_APPROVED`, `PAYMENT_FAILED` |
| `PAYMENT_APPROVED` | PG 승인, 내부 확정 전 | `PAID` |
| `PAID` | 결제와 내부 확정 완료 | `CANCEL_REQUESTED` |
| `PAYMENT_FAILED` | 명시적 결제 실패 | `PAYMENT_PENDING`, `EXPIRED` |
| `CANCEL_REQUESTED` | 환불 요청 또는 결과 불명 | `REFUNDED`, `CANCEL_FAILED` |
| `CANCEL_FAILED` | 환불 거절 | Core 종료 상태 |
| `REFUNDED` | 전액 환불과 재고 복구 완료 | 종료 |
| `EXPIRED` | 예약 만료와 재고 복구 완료 | 종료 |

`COMPLETED`, `CANCELED` enum 값은 남아 있지만 현재 command API에서 전환하지 않습니다.
