# 상품 정책

## 타입

- `NORMAL`: 대기열 없이 주문 검증으로 이동합니다.
- `LIMITED`: 유효한 admission token이 있어야 주문할 수 있고 한 주문에 단독으로만 담습니다.

## 상태와 구매 가능 조건

등록 가능한 상태는 `DRAFT`, `PREPARING`, `SCHEDULED`, `ACTIVE`입니다. 실제 구매는 다음 조건을 모두 만족해야 합니다.

- 상태가 `ACTIVE` 또는 `SCHEDULED`
- `saleStartAt`이 있으면 현재 시각이 시작 시각 이상
- `saleEndAt`이 있으면 현재 시각이 종료 시각 미만
- 재고가 요청 수량 이상

`SCHEDULED`는 미래의 `saleStartAt`이 필요하지만 `saleEndAt`은 선택입니다. 1초 주기의 스케줄러가 시작 시각이 된 상품을 `ACTIVE`로 전환합니다. 스케줄러 전환이 늦어도 재고 차감 SQL은 시작 시각이 지난 `SCHEDULED` 상품을 허용합니다.

## 재고와 구매 제한

- `initialStock`: 등록 당시 재고
- `stock`: 현재 예약 가능한 재고
- `soldCount`: 내부 결제 확정 수량
- `maxPurchaseQuantity`: 사용자·상품별 예약 수량과 결제 수량의 합계 제한

관리자는 `INCREASE` 또는 `DECREASE`와 양수 수량을 입력합니다. `ACTIVE`, `ARCHIVED` 상품은 관리자 재고 조정을 허용하지 않습니다. Core는 조정 결과만 상품에 반영하며 관리자 변경 사유와 별도 변경 이력은 이후 운영 단계로 미룹니다.
