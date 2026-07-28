# 상품 상태 정책

## 상태 의미

| 상태 | 의미 | 고객 목록 노출 |
| --- | --- | --- |
| `DRAFT` | 임시 저장 | 일반 목록 제외 |
| `PREPARING` | 판매 준비 | 포함 |
| `SCHEDULED` | 판매 일정 확정 | 포함 |
| `ACTIVE` | 판매 가능 | 포함 |
| `PAUSED` | 판매 일시 중지 | 포함 |
| `HIDDEN` | 비공개 | 일반 목록 제외 |
| `ARCHIVED` | 운영 종료·보관 | 일반 목록 제외 |

검색 API는 현재 상태 조건 없이 이름·설명을 검색하므로 위 일반 목록 기준과 달리
`DRAFT`, `HIDDEN`, `ARCHIVED`도 노출될 수 있습니다. 운영 전 검색 쿼리에 같은 노출
정책을 적용해야 합니다.

## 등록과 일정

등록 시 선택 가능한 상태는 `DRAFT`, `PREPARING`, `SCHEDULED`, `ACTIVE`입니다.

- 시작·종료가 모두 있으면 시작이 종료보다 빨라야 합니다.
- `SCHEDULED`는 시작과 종료가 모두 필요합니다.
- `SCHEDULED.saleStartAt`은 등록·수정 시점보다 미래여야 합니다.
- `ACTIVE.saleEndAt`은 현재보다 미래여야 합니다.

구매 가능 판정은 `ACTIVE` 또는 `SCHEDULED`이면서 시작 시각이 지났고 종료 시각은
지나지 않은 경우입니다. 재고 차감 SQL에서도 같은 시간 조건을 다시 확인합니다.

## 허용 상태 전이

| 현재 | 변경 가능 |
| --- | --- |
| `DRAFT` | `PREPARING`, `SCHEDULED`, `ACTIVE` |
| `PREPARING` | `SCHEDULED`, `ACTIVE`, `PAUSED`, `HIDDEN`, `ARCHIVED` |
| `SCHEDULED` | `ACTIVE`, `PAUSED`, `HIDDEN`, `ARCHIVED` |
| `ACTIVE` | `PAUSED`, `HIDDEN`, `ARCHIVED` |
| `PAUSED` | `PREPARING`, `SCHEDULED`, `ACTIVE`, `ARCHIVED` |
| `HIDDEN` | `PREPARING`, `SCHEDULED`, `ACTIVE`, `ARCHIVED` |
| `ARCHIVED` | 없음 |

같은 상태로 수정하는 것은 일정 등 다른 값을 변경하기 위해 허용됩니다.

## 자동 전이

상품 스케줄러는 1초 fixed delay로 시작 시각이 된 `SCHEDULED` 상품을 `ACTIVE`로
변경합니다. 스케줄러 실행 직전이라도 구매 판정과 DB 재고 차감은 시작 시각이 지난
`SCHEDULED` 상품을 허용합니다.

종료 시각이 지난 `ACTIVE` 상품은 구매할 수 없지만 상태를 `PAUSED`, `HIDDEN` 또는
`ARCHIVED`로 자동 변경하지는 않습니다. 관리자가 상태를 정리해야 합니다.

## 재고

- `initialStock`: 등록 당시 기준 수량
- `stock`: 현재 주문 가능한 실제 수량
- `soldCount`: 결제 확정 시 증가하는 판매 누계
- `maxPurchaseQuantity`: 한 주문의 상품별 최대 수량, null이면 제한 없음

관리자 재고 조정은 변경 이력과 사유를 남깁니다. 주문 만료·환불은 재고를 복구하고
품절 캐시도 transaction commit 이후 정리합니다.
