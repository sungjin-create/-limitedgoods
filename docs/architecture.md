# 아키텍처

이 문서는 `limitedgoods-backend`의 현재 실행 경로를 설명합니다. Outbox 기반 내부
후처리 방식은 [이벤트 처리 모드](./event-delivery-modes.md)에서 별도로 다룹니다.

## 구성 요소

```text
React client
  -> Spring Security + JWT filter
  -> REST controllers
  -> application services / domain policies
       ├─ PostgreSQL: 원본 데이터, 상태 이력, Outbox, 조회 projection
       ├─ Redis: 대기열, 입장 토큰, 요청 제한, 품절 캐시
       ├─ payment gateway adapter
       └─ application events -> Micrometer metrics

internal-worker profile
  -> Outbox claim/lease/retry
  -> analytics projection
  -> email delivery claim/lease/retry
```

PostgreSQL은 주문과 재고의 최종 기준입니다. Redis 데이터는 입장 제어와 빠른 실패
판단을 위한 보조 상태이며, Redis만으로 판매 재고를 확정하지 않습니다.

## 한정 상품 주문

1. 고객이 `POST /api/user/queue/enter`로 대기열에 진입합니다.
2. 앞에서 50명 안에 들면 5분 유효한 `admissionToken`을 받습니다.
3. 주문 생성 요청은 판매 상태·기간·구매 제한·토큰을 검증합니다.
4. PostgreSQL 조건부 update로 재고를 차감합니다. 재고 부족이면 주문은 생성되지 않습니다.
5. 주문은 `CREATED` 상태로 저장되고 재고는 5분 동안 예약됩니다.
6. 입장 토큰은 성공한 주문 생성 경로에서 소비됩니다.

일반 상품은 대기열과 입장 토큰을 사용하지 않습니다. 한정 상품은 다른 상품과 한
주문에 섞을 수 없습니다.

## 주문 생성 멱등성

클라이언트는 주문서 단위 `checkoutToken`을 보냅니다. 서버는 사용자와 토큰 조합을
유일하게 저장하고, 상품·수량으로 만든 fingerprint도 함께 비교합니다.

- 같은 토큰과 같은 요청: 기존 주문 응답 반환
- 같은 토큰과 다른 요청: `IDEMPOTENCY_KEY_REUSED` 오류
- 새 주문 생성 전 기존 `CREATED` 또는 `PAYMENT_FAILED` 주문: 만료 후 재고 복구
- 기존 `PAYMENT_PENDING` 또는 `PAYMENT_APPROVED` 주문: 새 주문 생성 거부

## 결제

```text
CREATED / PAYMENT_FAILED
  -> PAYMENT_PENDING
  -> PG 승인
  -> PAYMENT_APPROVED
  -> 내부 확정
  -> PAID
```

결제 요청에는 `Idempotency-Key`가 필수입니다. `payment_attempt`가 요청 key와
fingerprint, 금액, PG 거래 ID를 보관합니다.

- 동일 key·동일 요청은 저장된 결과를 재사용합니다.
- 동일 key·다른 요청은 거부합니다.
- 명시적 거절은 `DECLINED`와 주문 `PAYMENT_FAILED`로 기록합니다.
- 결과가 불명확하면 결제 시도는 `UNKNOWN`으로 남겨 reconciliation 대상이 됩니다.
- PG 승인과 주문의 `PAID` 확정을 나눠, 승인 후 내부 처리 실패를 다시 확정할 수 있습니다.

현재 결제 adapter는 프로젝트 내부 구현입니다. 실제 PG를 붙일 때는 사업자의 timeout,
webhook 서명, 승인 조회와 멱등성 보장을 다시 검증해야 합니다.

## 만료와 환불

주문 만료 스케줄러는 60초마다 `CREATED`, `PAYMENT_FAILED` 중 `expiresAt`이 지난
주문을 `EXPIRED`로 바꾸고 재고를 복구합니다. 따라서 5분 만료 시점과 실제 처리
사이에는 최대 한 번의 스케줄 간격이 추가될 수 있습니다.

환불은 현재 주문 전체만 지원합니다.

```text
PAID -> CANCEL_REQUESTED -> REFUNDED
                         -> CANCEL_FAILED -> CANCEL_REQUESTED (재시도)
```

환불 성공 시 상품 재고를 복구하고 `ORDER_CANCELED` Outbox 이벤트를 기록합니다.
`CANCELED` enum 값은 존재하지만 현재 고객 환불 완료 경로의 최종 상태는
`REFUNDED`입니다.

## Transactional Outbox

도메인 변경과 Outbox 쓰기는 같은 DB transaction 안에서 수행됩니다. 현재 완전한
로컬 실행 경로는 `internal-worker` 프로필입니다.

Worker는 처리할 행을 claim하고 lease를 부여한 다음 소비자별 멱등 테이블을 사용해
재처리를 막습니다. 실패한 이벤트는 backoff 후 재시도하고 최대 시도 횟수를 넘으면
`DEAD`가 됩니다. 통계와 이메일 소비자 중 하나라도 실패하면 Outbox 이벤트 자체도
성공 처리하지 않습니다.

기본 프로필 값은 300,000ms이고 `internal-worker` 프로필이 30,000ms로 덮어씁니다.
따라서 권장 로컬 실행의 기본 반영 주기는 30초입니다.

## 주기 작업

| 작업 | 기본 주기 | 역할 |
| --- | ---: | --- |
| 상품 상태 활성화 | 1초 fixed delay | 시작 시각이 된 `SCHEDULED`를 `ACTIVE`로 변경 |
| 주문 만료 | 60초 fixed delay | 예약 만료와 재고 복구 |
| 내부 Outbox | 30초 fixed delay | 통계·이메일 이벤트 처리 |
| 이메일 delivery | 설정 기반 | 전송 claim, 재시도와 DEAD 전환 |

판매 종료 시각이 지난 `ACTIVE` 상품은 주문 조건에서 거부되지만 상태를 자동으로
다른 값으로 바꾸는 스케줄러는 없습니다.

## 일관성 경계

- 주문·재고·Outbox: 한 PostgreSQL transaction
- 결제 PG 호출: DB transaction 밖의 외부 I/O를 포함하므로 `payment_attempt`로 복구
- 통계·이메일: 비동기 projection이므로 원본 주문보다 늦게 보일 수 있음
- 대기열·토큰: Redis 상태, 최종 재고 보장은 PostgreSQL 조건부 차감
- 메트릭: 운영 관측용이며 도메인 원본 데이터가 아님
