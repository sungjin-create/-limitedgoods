# API 요약

기본 주소는 `http://localhost:8080`입니다. 응답은 공통 `ApiResponse` envelope로
감싸며, 화면에서는 그 안의 `data`를 사용합니다.

관리자 API는 `ADMIN` 역할이 필요합니다. 사용자별 데이터를 다루는 API도 유효한
JWT principal을 전제로 구현돼 있으므로 다음 헤더를 보냅니다.

```http
Authorization: Bearer <access-token>
```

## 회원과 상품

| Method | Endpoint | 설명 | 주요 입력 |
| --- | --- | --- | --- |
| POST | `/api/user/signup` | 회원가입 | email, password, name |
| POST | `/api/user/login` | 로그인과 JWT 발급 | email, password |
| GET | `/api/user/info` | 현재 사용자 정보 | JWT |
| GET | `/api/product` | 상품 목록 | `page`, `size`, `sort` |
| GET | `/api/product/search` | 상품 검색 | `keyword`, page 조건 |

상품 목록은 `PREPARING`, `SCHEDULED`, `ACTIVE`, `PAUSED`를 노출합니다. 현재 검색
쿼리는 상태 조건을 적용하지 않으므로 `DRAFT`, `HIDDEN`, `ARCHIVED`도 검색 결과에
포함될 수 있습니다.

## 장바구니와 대기열

| Method | Endpoint | 설명 | 주요 입력 |
| --- | --- | --- | --- |
| GET | `/api/cart` | 내 장바구니 | JWT |
| POST | `/api/cart/item/add` | 상품 추가 | `productId`, `quantity` |
| POST | `/api/cart/item/update` | 수량 변경 | `cartItemId`, `quantity` |
| DELETE | `/api/cart/item` | 항목 삭제 | query `cartItemId` |
| POST | `/api/user/queue/enter` | 한정 상품 대기열 진입 | `productId` |
| GET | `/api/user/queue/status` | 대기·입장 상태 조회 | query `productId` |

대기 상태는 프론트엔드에서 2~3초 간격으로 조회합니다. `admitted=true` 응답의
`admissionToken`은 한정 상품 주문 요청에 포함합니다.

## 주문과 결제

| Method | Endpoint | 설명 | 필수 사항 |
| --- | --- | --- | --- |
| POST | `/api/user/order/create` | 주문 생성과 재고 예약 | `checkoutToken`, items |
| POST | `/api/user/order/{orderId}/pay` | 결제 | `Idempotency-Key` 헤더 |
| GET | `/api/user/order` | 내 주문 목록 | JWT |
| GET | `/api/user/order/{orderId}` | 내 주문 상세 | JWT |
| POST | `/api/user/order/{orderId}/cancel` | 결제 주문 전체 환불 | `PAID` 주문 |
| POST | `/api/user/order/{orderId}/refund/retry` | 실패 환불 재시도 | `CANCEL_FAILED` 주문 |

주문 생성 예시:

```json
{
  "checkoutToken": "checkout-20260728-0001",
  "items": [
    { "productId": 16, "quantity": 1 }
  ],
  "admissionToken": "limited-product-admission-token"
}
```

`checkoutToken`은 필수이며 최대 255자입니다. 항목은 최대 50개이고 같은 상품을
두 줄로 보낼 수 없습니다. `admissionToken`은 일반 상품에는 생략합니다.

결제 요청 예시:

```http
POST /api/user/order/123/pay
Authorization: Bearer <access-token>
Idempotency-Key: payment-123-attempt-1
Content-Type: application/json

{"forceFail": false}
```

`forceFail`은 현재 내부 결제 adapter의 실패 시나리오를 시험하기 위한 값입니다.

## 관리자

모든 endpoint에 `ADMIN` JWT가 필요합니다.

| Method | Endpoint | 설명 | query/body |
| --- | --- | --- | --- |
| GET | `/api/admin/backoffice/product` | 상품 목록 | 선택 `status` |
| POST | `/api/admin/backoffice/product/register` | 상품 등록 | 상품·재고·일정 |
| PUT | `/api/admin/backoffice/product/update` | 상품과 상태 수정 | 상품 ID 포함 |
| PUT | `/api/admin/backoffice/product/stock` | 재고 조정 | 상품 ID, 수량, 사유 |
| GET | `/api/admin/backoffice/product/{productId}/stock-overview` | 재고 구성 조회 | path ID |
| GET | `/api/admin/backoffice/order` | 주문 조회 | 선택 `startAt`, `endAt` ISO date-time |
| PATCH | `/api/admin/backoffice/order/{orderId}/complete` | `PAID` 주문 완료 | path ID |
| GET | `/api/admin/backoffice/dashboard` | 운영 요약과 최근 주문 | 없음 |
| GET | `/api/admin/backoffice/analytics/overview` | 일별 통계와 기간 합계 | `from`, `to` |
| GET | `/api/admin/backoffice/analytics/products` | 기간별 상품 순위 | `from`, `to`, 선택 `limit` |
| GET | `/api/admin/backoffice/monitoring/overview` | Prometheus 기반 운영 지표 | 없음 |
| GET | `/api/admin/backoffice/monitoring/business` | DB 기반 비즈니스 지표 | 없음 |
| GET | `/api/admin/email-deliveries/dead` | DEAD 메일 목록 | page 조건 |
| POST | `/api/admin/email-deliveries/{deliveryId}/retry` | DEAD 메일 재대기 | path ID |

통계 날짜는 `YYYY-MM-DD` 형식이고 양 끝 날짜를 모두 포함합니다. 조회 기간은 최대
366일입니다. 상품 순위 `limit`은 기본 10, 허용 범위 1~100입니다.

## 운영 endpoint

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/actuator/health` | 애플리케이션·의존성 상태 |
| GET | `/actuator/info` | 애플리케이션 정보 |
| GET | `/actuator/prometheus` | Prometheus scrape 형식 메트릭 |

현재 Security 설정은 `/api/admin/**`만 명시적으로 역할을 강제하고 나머지는
`permitAll`입니다. 다만 사용자 주문·장바구니·대기열 구현은 principal을 바로
사용하므로 인증 없이 호출하면 정상적인 비즈니스 응답을 보장하지 않습니다. 운영
전에는 보호 경로를 Security 설정에도 명시하는 것이 안전합니다.

