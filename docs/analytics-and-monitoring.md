# 통계와 모니터링

통계는 주문 테이블을 매 요청마다 집계하지 않고 Outbox 이벤트로 갱신되는 read
projection을 조회합니다. 따라서 원본 주문 상태와 화면 사이에 Worker 처리 지연이
있을 수 있습니다.

## Projection

| 테이블 | 기준 | 주요 값 |
| --- | --- | --- |
| `daily_sales_projection` | 결제일·환불 발생일 | 결제 주문, 총매출, 환불 주문·금액, 판매·환불·순수량 |
| `daily_order_funnel_projection` | 주문 생성일 cohort | 생성, 결제, 결제 실패, 만료, 환불 주문 |
| `product_sales_projection` | 상품 전체 누계 | 결제 주문, 판매·환불 수량, 총·환불·순매출 |

상품 순위 API는 전체 누계 projection을 그대로 정렬하지 않습니다. 요청 기간의
`paidAt`과 `refundedAt`을 원본 주문·주문 항목에서 다시 모아 순매출, 순수량 순으로
정렬합니다.

## 날짜 귀속 기준

- 총매출과 판매 수량: 결제가 완료된 `paidAt` 날짜
- 환불액과 환불 수량: 환불이 완료된 `canceledAt` 이벤트 날짜
- 주문 퍼널의 모든 단계: 해당 주문의 `createdAt` 날짜

따라서 과거 주문이 오늘 환불되면 오늘 순매출은 음수가 될 수 있습니다. 이는
거래 발생일 기준 순매출의 결과입니다. 반면 퍼널은 “오늘 발생한 모든 처리”가 아니라
“오늘 생성된 주문이 현재 어느 단계까지 전환됐는가”를 나타냅니다.

## 지표 정의

| 지표 | 계산 |
| --- | --- |
| 순매출 | `grossRevenue - refundAmount` |
| 순판매수량 | `soldQuantity - refundedQuantity` |
| 객단가 | `grossRevenue / paidOrderCount` |
| 결제 전환율 | `paidOrderCount / createdOrderCount × 100` |
| 만료율 | `expiredOrderCount / createdOrderCount × 100` |
| 환불률 | `canceledOrderCount / paidOrderCount × 100` |

분모가 0이면 비율은 0으로 반환하고, 비율은 소수점 한 자리로 반올림합니다.

## 관리자 화면

- 상단 카드는 오늘 00:00부터 현재까지의 주문, 순매출, 결제 전환율, 환불을 표시합니다.
- 실시간 수치를 어제 하루 전체와 직접 비교하지 않습니다.
- 별도 확정 비교는 어제 하루와 그제 하루를 비교합니다.
- 차트와 상품 순위는 동일한 최근 7일 또는 30일 범위를 사용합니다.
- 일별 데이터가 없는 날짜도 API가 0으로 채워 연속된 날짜 배열을 반환합니다.

## API

```http
GET /api/admin/backoffice/analytics/overview?from=2026-07-22&to=2026-07-28
GET /api/admin/backoffice/analytics/products?from=2026-07-22&to=2026-07-28&limit=10
```

- `from`, `to`: 필수, 양 끝 날짜 포함
- 최대 기간: 366일
- 상품 `limit`: 기본 10, 1~100
- 인증: `ADMIN` JWT

## Outbox 반영 지연

`internal-worker` 프로필은 기본 30초 fixed delay로 Outbox를 처리합니다. claim된
이벤트는 `internal_processed_event`의 `eventId + consumerName` 유일 조건으로
중복 반영을 막습니다. 실패 시 재시도되며 처리 전에는 통계에 나타나지 않습니다.

## 애플리케이션 모니터링

Actuator는 다음 endpoint를 노출합니다.

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`

Prometheus는 Compose 컨테이너에서 `host.docker.internal:8080`을 15초마다
scrape합니다. Grafana datasource와 dashboard는 `monitoring/grafana/provisioning`에서
자동 등록됩니다.

커스텀 메트릭:

| Micrometer 이름 | Prometheus 이름 예 | 설명 |
| --- | --- | --- |
| `limitedgoods.order.create` | `limitedgoods_order_create_total` | 주문 생성 성공·실패와 사유 |
| `limitedgoods.order.expired` | `limitedgoods_order_expired_total` | 만료 건과 사유 |
| `limitedgoods.payment` | `limitedgoods_payment_total` | 결제 결과와 사유 |
| `limitedgoods.payment.revenue` | `limitedgoods_payment_revenue_total` | 승인 매출 누계 |

관리자 모니터링 API는 Prometheus 기반 요청량·오류율·지연·가용성·JVM·DB pool
지표와 DB 기반 주문·재고 지표를 화면용 응답으로 조합합니다.

## 현재 확인된 주의점

- `init.sql`은 `daily_sales_projection.refunded_quantity`를 포함하지 않습니다. 실제
  애플리케이션 schema 기준은 Flyway `V1`과 `V2` migration입니다.
- projection 재구축용 운영 command는 아직 없습니다. 데이터 보정 시 멱등 테이블과
  projection을 함께 다루는 별도 절차가 필요합니다.

