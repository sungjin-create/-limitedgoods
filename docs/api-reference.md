# API

브라우저 로컬 개발 기준 API 주소는 `http://127.0.0.1:8080`입니다. 응답은 `ApiResponse`의 `success`, `code`, `message`, `data` 구조를 사용합니다.

인증이 필요한 요청은 다음 헤더를 보냅니다.

```http
Authorization: Bearer <access-token>
```

## 인증과 사용자

| Method | Endpoint | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/user/signup` | 없음 | 회원가입 |
| POST | `/api/user/login` | 없음 | Access Token과 Refresh cookie 발급 |
| POST | `/api/user/refresh` | Refresh cookie | token 회전과 새 Access Token 발급 |
| POST | `/api/user/logout` | 선택 | Refresh Token 폐기와 cookie 만료 |
| GET | `/api/user/info` | USER/ADMIN | 내 정보 |

Access Token은 응답 `data.accessToken`으로 받고, Refresh Token은 `refresh_token` HttpOnly cookie로만 전달됩니다. 브라우저 요청은 cookie 전송을 위해 credentials를 포함해야 합니다. 로컬에서는 cookie의 SameSite 정책이 일관되도록 프런트와 API host를 `127.0.0.1`로 통일합니다.

## 상품

| Method | Endpoint | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/product` | 없음 | 상품 목록 |
| GET | `/api/product/search?keyword=...` | 없음 | 상품 검색 |

상품 목록과 검색은 모두 `PREPARING`, `SCHEDULED`, `ACTIVE`, `PAUSED` 상태만 노출합니다. `DRAFT`, `HIDDEN`, `ARCHIVED`는 공개 API에서 조회되지 않습니다.

## 대기열

| Method | Endpoint | 입력 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/user/queue/enter` | body `productId` | 대기열 진입 |
| GET | `/api/user/queue/status` | query `productId` | 순번 또는 입장 token 조회 |
| POST | `/api/user/queue/heartbeat` | body `productId` | 대기 사용자 활동 갱신 |
| DELETE | `/api/user/queue/leave` | query `productId` | 대기열 이탈 |

모두 로그인이 필요합니다. `admitted=true`이면 받은 `admissionToken`을 한정 상품 주문에 사용합니다.

## 주문·결제·환불

| Method | Endpoint | 필수 입력 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/user/order/create` | `checkoutToken`, `items`, 한정 상품의 `admissionToken` | 주문과 재고 예약 |
| POST | `/api/user/order/{orderId}/pay` | `Idempotency-Key` 헤더 | 결제 요청 |
| GET | `/api/user/order` | JWT | 내 주문 목록 |
| GET | `/api/user/order/{orderId}` | JWT | 내 주문 상세 |
| POST | `/api/user/order/{orderId}/cancel` | `PAID` 주문 | 전액 환불 |

주문 항목은 1~50개이며 상품 ID와 수량은 양수여야 합니다. 같은 상품을 두 항목으로 보낼 수 없습니다. 결제 멱등 키는 8~100자의 영문·숫자 및 `._:-` 조합입니다.

## 관리자 상품 기능

모두 `ADMIN` 역할이 필요합니다.

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/api/admin/backoffice/product` | 모든 상태의 관리자 상품 목록 |
| POST | `/api/admin/backoffice/product/register` | 상품 등록 |
| PUT | `/api/admin/backoffice/product/sale-settings` | 일반/한정 타입과 판매 시작 시각 설정 |
| PUT | `/api/admin/backoffice/product/stock` | 재고 증가 또는 감소 |

관리자 주문 조회, 환불 재시도, 대시보드와 모니터링 API는 Core에 없습니다.

## 상태 확인

관리 포트 기본값은 `9091`입니다.

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `http://localhost:9091/actuator/health` | 애플리케이션과 의존성 상태 |
