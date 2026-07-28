# Limited Goods 문서

이 디렉터리는 현재 코드가 실제로 수행하는 동작을 기준으로 작성합니다. 계획 또는 미구현 기능은 구현된 기능과 구분해 표시합니다.

## 문서 목록

| 문서 | 내용 |
| --- | --- |
| [아키텍처](./architecture.md) | 주요 컴포넌트와 주문·결제·Outbox 흐름 |
| [API 요약](./api-reference.md) | 사용자·관리자 endpoint와 필수 헤더 |
| [통계와 모니터링](./analytics-and-monitoring.md) | projection, 날짜 기준, Prometheus/Grafana |
| [이벤트 처리 모드](./event-delivery-modes.md) | internal-worker의 실행 방식과 장애 처리 |
| [Worker 타임라인](./worker-timeline.md) | Outbox와 이메일 Worker의 claim·lease·retry 흐름 |
| [도메인 정책](./policies/README.md) | 대기열·주문·결제·상품 정책 |

## 문서 갱신 규칙

- 상태명, endpoint, 제한값은 코드의 값과 동일하게 유지합니다.
- 화면 문구와 통계 집계 기준을 함께 변경합니다.
- 새 DB 변경은 기존 migration을 수정하지 않고 새 Flyway migration으로 추가합니다.
- 미구현 기능을 현재 동작처럼 서술하지 않습니다.
- 운영 기본값과 개발 기본값을 구분합니다.
