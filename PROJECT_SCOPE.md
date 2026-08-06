# 프로젝트 범위

## 목표

한정 상품에 주문이 집중되는 상황에서 다음 두 가지를 검증합니다.

1. PostgreSQL transaction과 제약 조건으로 초과 판매와 중복 주문을 방지한다.
2. Redis admission queue로 DB에 진입하는 동시 요청을 제한한다.

처리 가능한 최대 사용자 수를 주장하는 프로젝트가 아니라, **대기열 도입 전후의 차이와 동시성 정합성**을 설명하는 프로젝트입니다.

## 핵심 범위

- Redis Sorted Set 대기열과 admission token
- token claim, consume, release와 generation 무효화
- PostgreSQL 조건부 재고 차감
- 사용자·상품별 구매 한도 원자 예약
- `checkoutToken` 기반 주문 멱등성
- 다상품 주문의 lock 순서 통일
- direct/queue 동일 조건 비교 실험
- 테스트 종료 후 DB 정합성 확인

## 보조 범위

실험 가능한 주문 흐름을 구성하기 위해 인증, 상품 관리, 결제, 주문 만료와 전액 환불 기능을 유지합니다. 이 기능의 포괄적인 E2E·성능 검증은 Core 완료 조건에 포함하지 않습니다.

## 제외

- 상품 조회·검색 성능 최적화
- 혼합 사용자 트래픽 모델
- 장시간 soak와 최대 용량 spike
- 실제 PG·webhook 운영 검증
- 결제·환불·인증 전체 회귀 테스트
- 프런트엔드 E2E와 접근성 테스트
- Prometheus·Grafana·커스텀 메트릭
- Kafka/Outbox, Kubernetes와 자동 확장

## 완료 기준

- 집중된 동시 주문에서도 재고와 구매 제한이 음수가 되거나 초과되지 않습니다.
- 동일 `checkoutToken`은 주문을 하나만 만듭니다.
- 다상품 역순 주문이 deadlock 없이 완료됩니다.
- Redis token 상태 전이와 품절 generation이 실제 Redis에서 동작합니다.
- 동일한 초기 조건의 queue/direct 비교 결과와 DB 정합성 결과를 보고서에 남깁니다.
