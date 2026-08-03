# 결제·환불 정책

## 결제 멱등성

결제 요청은 8~100자의 `Idempotency-Key`와 요청 fingerprint를 사용합니다. 주문과 key 조합은 DB unique 제약으로 보호합니다.

- 같은 key와 같은 fingerprint: 기존 결제 시도를 재사용
- 같은 key와 다른 fingerprint: 재사용 오류
- `DECLINED`: 같은 시도를 다시 승인 요청하지 않음
- `PROCESSING`, `UNKNOWN`: 같은 key로 PG 결과 조회

## 승인과 내부 확정

PG 승인과 `PAID` 전환은 분리합니다. 승인 금액이 주문 금액과 다르면 내부 확정을 진행하지 않고 보상 필요 상태로 기록합니다.

`PAYMENT_APPROVED` 주문은 PG를 다시 호출하지 않고 내부 확정만 재시도합니다. 내부 확정은 구매 제한 예약을 결제 수량으로 이동하고 상품 `soldCount`를 증가시킵니다.

## 실패와 timeout

- 명시적 거절: 결제 시도 `DECLINED`, 주문 `PAYMENT_FAILED`
- timeout·network 오류: 결제 시도 `UNKNOWN`, 주문은 결과 확정 전 상태 유지
- 예약 기한 전 `PAYMENT_FAILED` 주문: 새 key로 결제 가능
- 예약 기한 경과: 주문 만료와 예약 복구

Core의 결제 adapter는 실제 PG가 아닌 테스트용 구현입니다. 실제 PG 도입 시 승인 조회 API와 webhook 정책을 별도로 구현해야 합니다.

## 환불

부분 환불은 지원하지 않습니다. `PAID` 주문의 승인된 PG 거래를 전액 환불합니다.

- 승인: `REFUNDED`, 환불 시각 저장, 재고 및 결제 구매 수량 복구
- 명시적 거절: `CANCEL_FAILED`
- timeout·처리 중: `CANCEL_REQUESTED`와 환불 시도 `UNKNOWN` 유지

Core에는 결과 불명 환불 자동 reconciliation, 관리자 환불 재시도, 환불 운영 API가 없습니다.
