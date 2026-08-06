# Limited Goods Core

한정 상품 주문이 한꺼번에 들어올 때 **데이터 정합성을 지키면서 DB 진입 경합을 줄이는 방법**을 검증하는 Spring Boot 프로젝트입니다.

## 핵심 문제

- 재고보다 많은 동시 주문이 들어와도 초과 판매하지 않아야 합니다.
- 같은 주문 재시도가 재고를 중복 차감하면 안 됩니다.
- 사용자별 구매 제한을 여러 요청으로 우회할 수 없어야 합니다.
- 품절될 요청까지 모두 DB transaction에 진입하지 않아야 합니다.

## 해결 방식

### PostgreSQL: 최종 정합성

- `stock >= quantity` 조건부 update
- 구매 counter의 `ON CONFLICT ... WHERE`
- 사용자와 `checkoutToken` unique 제약
- 다상품 주문의 상품 ID 정렬로 lock 순서 통일

### Redis: 진입 제어

- 상품별 Sorted Set 대기열
- active window 안의 사용자에게 admission token 발급
- 주문 시작 시 claim, 성공 시 consume, 실패 시 release
- 품절 시 generation 증가로 이전 token 무효화

대기열 통과는 구매 성공을 보장하지 않습니다. 최종 판매 여부는 항상 PostgreSQL transaction이 결정합니다.

## 핵심 검증 결과

재고 100개인 상품에 사용자 5,000명이 10초 동안 접근하도록 Queue와 Direct를 같은 조건에서 비교했습니다. 두 모드 모두 5,000명을 완료하고 dropped iteration이 없었던 2차 실행을 대표값으로 사용했습니다.

| 지표 | Direct | Queue |
| --- | ---: | ---: |
| 주문 API 진입 | 5,000건 | 104건 |
| 생성된 주문 | 100건 | 100건 |
| 예상하지 않은 오류 | 0건 | 0건 |

Queue 적용 후 준비된 재고만큼만 주문되는 정합성을 유지하면서 주문 API와 DB transaction 진입 후보를 97.92% 줄였습니다. 자세한 조건과 해석은 [Core 검증 결과](./docs/report/core-test-result.md)에 정리했습니다.

Queue의 전체 흐름 p95는 Direct보다 길었습니다. 따라서 Queue를 응답시간 개선 수단으로 주장하지 않고, 트래픽이 집중되는 LIMITED 상품의 DB 보호를 위한 선택적 admission control로 사용합니다.

## 남긴 테스트

| 구분 | 검증 내용 |
| --- | --- |
| 단위 테스트 | 주문 사전 조건, admission 조정, 상품 판매 조건, queue snapshot·정리 |
| PostgreSQL 통합 테스트 | 재고, 주문 멱등성, 활성 주문, deadlock, 구매 한도 경합 |
| Redis 통합 테스트 | queue 유지보수, token 상태 전이, 품절 generation |
| k6 | smoke, 핫 상품 주문, queue/direct 비교 |

```powershell
.\gradlew.bat test
.\gradlew.bat integrationTest
```

## 로컬 실행

```powershell
Copy-Item .env.example .env
docker compose up -d
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

| 대상 | 주소 |
| --- | --- |
| API | `http://127.0.0.1:8080` |
| Health | `http://localhost:9091/actuator/health` |
| PostgreSQL | `localhost:5432`, DB `limitedgoods_core` |
| Redis | `localhost:6379` |

## 보조 구현

JWT/Refresh Token 인증, 결제 멱등성, 주문 만료, 전액 환불과 관리자 상품 API도 동작합니다. 이 기능들은 대기열·동시성 실험을 수행하기 위한 애플리케이션 흐름으로 유지하며, Core 성과의 중심으로 주장하지 않습니다.

## 문서

- [프로젝트 범위](./PROJECT_SCOPE.md)
- [아키텍처](./docs/architecture.md)
- [Core 검증 결과](./docs/report/core-test-result.md)
- [핵심 테스트 계획](./docs/report/core-test-completion-plan.md)
- [k6 대기열 비교](./k6/README.md)
- [API 참고](./docs/api-reference.md)
- [세부 정책](./docs/policies/README.md)
- [설계 결정](./docs/decisions/README.md)
