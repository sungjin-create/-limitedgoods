# 대기열 도입 효과 실험

최종 측정값과 해석은 [Core 대기열·동시성 검증 결과](../docs/report/core-test-result.md)를 참고합니다.

k6는 세 가지 용도로만 사용합니다.

1. `k6-smoke-test.js`: 환경과 핵심 API 확인
2. `k6-order-test.js`: 한정 상품 queue → 주문 흐름 확인
3. `k6-queue-comparison-test.js`: 동일 부하의 queue/direct 비교

`k6-prepare-users.js`와 `prepare-access-tokens.mjs`는 실험 데이터 준비 도구입니다.

## 1. 환경 시작

```powershell
Copy-Item .env.loadtest.example .env.loadtest
docker compose --env-file .env.loadtest -f docker-compose.loadtest.yml up -d --build
Invoke-RestMethod http://localhost:19091/actuator/health
```

## 2. 사용자와 상품 준비

최종 비교 실험은 사용자 5,000명을 사용합니다. 준비 작업은 Queue와 Direct 환경을 새로 만들 때마다 각각 실행합니다.

```powershell
k6 run -e USER_COUNT=5000 .\k6\k6-prepare-users.js
node .\k6\lib\prepare-access-tokens.mjs --user-count 5000

Get-Content .\k6\seed-loadtest-products.sql -Raw |
  docker compose --env-file .env.loadtest -f docker-compose.loadtest.yml `
  exec -T postgres psql -U loadtest -d limitedgoods_loadtest
```

seed 출력에서 실제 핫 상품 ID와 초기 재고를 기록합니다.

seed는 DB에 직접 상품을 추가합니다. 애플리케이션 시작 시 실행되는 LIMITED 상품 상태 warmup에 새 상품을 포함하려면 seed 후 app을 재시작해야 합니다.

```powershell
docker compose --env-file .env.loadtest `
  -f docker-compose.loadtest.yml `
  restart app

Invoke-RestMethod http://localhost:19091/actuator/health
```

재시작하지 않으면 Redis에 queue state가 없어 `QUEUE_008`이 발생할 수 있습니다. 준비된 access token의 기본 저장 위치는 `k6/lib/access-tokens.json`입니다.

## 3. smoke

```powershell
k6 run .\k6\k6-smoke-test.js
```

## 4. queue 주문 흐름

이 테스트는 Queue에서 주문까지 이어지는 기능 흐름을 확인하기 위한 것으로, Queue/Direct 효과 비교에는 사용하지 않습니다.

```powershell
k6 run `
  -e USER_COUNT=1000 `
  -e ORDER_VUS=200 `
  -e HOT_PRODUCT_ID=16 `
  .\k6\k6-order-test.js
```

## 5. queue/direct 비교

두 실행은 동일한 DB·Redis 초기 상태에서 시작해야 합니다.
`seed-loadtest-products.sql`은 기존 상품의 재고를 되돌리지 않으므로 각 실행 전에 loadtest volume을 새로 만드는 방법을 사용합니다.

최종 비교 프로필은 사용자 5,000명, 초당 500명, 10초입니다. 대표 결과는 두 모드 모두 `dropped_iterations=0`인 실행끼리만 비교합니다.

### queue

```powershell
docker compose --env-file .env.loadtest `
  -f docker-compose.loadtest.yml `
  -f docker-compose.loadtest-queue.yml `
  down -v --remove-orphans

docker compose --env-file .env.loadtest `
  -f docker-compose.loadtest.yml `
  -f docker-compose.loadtest-queue.yml `
  up -d --build --force-recreate app
```

사용자·토큰·상품을 2절과 같이 준비하고 app을 재시작한 뒤 실행합니다.

```powershell
k6 run `
  --summary-export=k6/results/comparison-queue-5000-run1.json `
  -e MODE=queue `
  -e AUTH_MODE=prepared `
  -e TOKEN_FILE=./lib/access-tokens.json `
  -e HOT_PRODUCT_ID=16 `
  -e USER_COUNT=5000 `
  -e ARRIVAL_RATE=500 `
  -e ARRIVAL_DURATION=10s `
  -e PRE_ALLOCATED_VUS=2000 `
  -e MAX_VUS=3000 `
  .\k6\k6-queue-comparison-test.js
```

결과와 [DB 정합성 SQL](../docs/test/load-test-acceptance-criteria.md)을 저장한 뒤 Direct 환경을 준비합니다.

### direct

```powershell
docker compose --env-file .env.loadtest `
  -f docker-compose.loadtest.yml `
  -f docker-compose.loadtest-queue.yml `
  down -v --remove-orphans

docker compose --env-file .env.loadtest `
  -f docker-compose.loadtest.yml `
  -f docker-compose.loadtest-direct.yml `
  up -d --build --force-recreate app
```

사용자·토큰·상품을 다시 준비하고 app을 재시작한 뒤 실행합니다.

```powershell
k6 run `
  --summary-export=k6/results/comparison-direct-5000-run1.json `
  -e MODE=direct `
  -e AUTH_MODE=prepared `
  -e TOKEN_FILE=./lib/access-tokens.json `
  -e HOT_PRODUCT_ID=16 `
  -e USER_COUNT=5000 `
  -e ARRIVAL_RATE=500 `
  -e ARRIVAL_DURATION=10s `
  -e PRE_ALLOCATED_VUS=2000 `
  -e MAX_VUS=3000 `
  .\k6\k6-queue-comparison-test.js
```

`HOT_PRODUCT_ID`는 예시값을 그대로 쓰지 않고 매번 seed 출력값으로 바꿉니다.

반복 실행도 PostgreSQL volume과 Redis를 다시 초기화해야 합니다. 주문 만료로 재고가 복구된 기존 환경을 그대로 사용하면 JVM·DB warm 상태와 이전 주문 데이터가 결과에 섞입니다.

`--summary-export`는 같은 파일명을 사용하면 이전 결과를 덮어씁니다. 반복 결과를 보존하려면 `comparison-direct-5000-run2.json`처럼 실행마다 다른 이름을 사용합니다.

## 6. 판정

- `unexpected_errors`: 실제 HTTP/API 장애
- `business_rejections`: 품절·구매 제한 등 정상 거절
- `comparison_completed_journeys`: 완료된 사용자 흐름
- `comparison_queue_wait_duration`: queue 대기 시간
- `comparison_journey_duration`: 전체 흐름 시간
- `dropped_iterations`: 실행하지 못한 요청

Queue와 Direct 중 한쪽에 `dropped_iterations`가 있으면 실제 처리한 사용자 수가 달라지므로 공식 비교값으로 사용하지 않습니다. 응답시간 threshold 실패도 숨기지 않고 기록하되, 비즈니스 정합성 실패와 구분합니다.

k6 결과만으로 성공을 판정하지 않습니다. 각 실행 직후 [합격 기준과 DB SQL](../docs/test/load-test-acceptance-criteria.md)을 확인하고 [Core 검증 결과](../docs/report/core-test-result.md)와 같은 형식으로 기록합니다.
