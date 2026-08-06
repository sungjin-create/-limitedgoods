# 대기열·동시성 아키텍처

## 문제

재고가 적은 상품에 요청이 몰리면 실제로 성공할 수 있는 주문보다 훨씬 많은 transaction이 PostgreSQL에 진입합니다. DB가 정합성을 지키더라도 connection과 row lock 경쟁은 증가합니다.

## 요청 흐름

```text
사용자
  -> Redis Sorted Set 대기열
  -> active window 진입
  -> admission token 발급
  -> 주문 시작 시 token claim
  -> PostgreSQL 주문 transaction
       1. 기존 활성 주문과 checkoutToken 확인
       2. 상품 ID 순서로 재고 조건부 차감
       3. 사용자별 구매 한도 예약
       4. 주문과 주문 항목 저장
  -> 성공 시 token consume / 실패 시 release
```

## 역할 분리

| 구성요소 | 책임 |
| --- | --- |
| Redis | 대기 순서, 동시 진입 수 제한, 빠른 품절 거절 |
| PostgreSQL | 재고, 구매 한도, 주문의 최종 정합성 |

Redis 값이 오래됐거나 token을 발급했더라도 판매 성공은 확정되지 않습니다. 모든 주문은 PostgreSQL 검증을 통과해야 합니다.

## 동시성 보호

### 재고

`stock >= quantity`인 경우에만 한 번의 update로 차감합니다. 애플리케이션에서 재고를 읽은 뒤 별도로 저장하지 않습니다.

### 구매 한도

사용자·상품 counter를 `ON CONFLICT ... WHERE`로 갱신해 여러 주문이 동시에 들어와도 예약 수량과 결제 수량의 합이 제한을 넘지 않게 합니다.

### 주문 멱등성

사용자와 `checkoutToken` 조합에 unique 제약을 둡니다. token이 같고 상품·수량 fingerprint가 같으면 기존 주문을 반환하고, 내용이 다르면 충돌로 거절합니다.

### deadlock 완화

다상품 주문은 요청 순서와 무관하게 상품 ID 순서로 처리해 transaction의 lock 획득 순서를 통일합니다.

## 대기열 상태

```text
WAITING -> READY -> PROCESSING -> CONSUMED
                       |
                       +-> READY (주문 실패 시 release)
```

품절 또는 판매 설정 변경 시 generation을 증가시킵니다. 이전 generation token은 더 이상 주문 진입에 사용할 수 없습니다.

## 검증 방식

- PostgreSQL Testcontainers에서 latch로 시작점을 맞춘 동시 주문 실행
- Redis 7 Testcontainers에서 실제 Lua와 TTL·generation 검증
- 동일한 초기 재고와 요청률로 queue/direct k6 비교
- 응답 성공률뿐 아니라 종료 후 DB 불변식 확인
