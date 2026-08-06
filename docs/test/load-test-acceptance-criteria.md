# 대기열 비교 합격 기준

## 목적

최대 처리 용량을 인증하는 테스트가 아니라, 동일한 집중 주문에서 Redis admission queue가 DB 진입 경합을 완화하는지 비교합니다.

## 실험 통제

queue와 direct 실행은 다음 조건이 같아야 합니다.

- commit과 애플리케이션 설정
- 사용자 수와 access token
- 상품 초기 재고
- arrival rate와 duration
- DB pool과 server thread
- Docker 자원

두 실행 사이에는 PostgreSQL과 Redis를 초기 상태로 다시 준비합니다.

## 필수 합격 조건

- 예상하지 않은 오류율 `< 0.1%`
- dropped iteration `0`
- 재고 음수 `0건`
- 초기 재고를 넘는 주문 `0건`
- 동일 사용자·checkout token 중복 `0건`
- 구매 제한 초과 `0건`

품절과 구매 제한은 예상된 비즈니스 거절로 분리하며 시스템 오류에 포함하지 않습니다.

## 비교 지표

- 완료된 사용자 흐름
- 주문 API 진입 횟수
- 생성된 주문
- 품절까지 걸린 전체 journey 시간
- queue 대기 시간
- 주문 API p95
- timeout과 connection 오류

대기열의 효과는 지연 시간 하나로 단정하지 않습니다. 동일한 정합성을 유지하면서 불필요한 주문 transaction과 장애가 줄었는지 함께 해석합니다.

응답시간을 비교할 때는 각 모드의 cold/warm 상태를 동일하게 맞춥니다. 초기화하지 않은 반복 실행은 이전 주문, 주문 만료, JVM과 DB cache의 영향을 받으므로 최초 실행과 직접 비교하지 않습니다.

최종 실행 결과는 [Core 대기열·동시성 검증 결과](../report/core-test-result.md)에 기록합니다.

## DB 확인

```sql
SELECT id, initial_stock, stock, sold_count
FROM product
WHERE id = :productId;

SELECT user_id, checkout_token, COUNT(*)
FROM orders
GROUP BY user_id, checkout_token
HAVING COUNT(*) > 1;

SELECT user_id, product_id, reserved_quantity, paid_quantity, purchase_limit
FROM user_product_purchase_counter
WHERE reserved_quantity < 0
   OR paid_quantity < 0
   OR reserved_quantity + paid_quantity > purchase_limit;
```
