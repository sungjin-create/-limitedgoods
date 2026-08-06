# 핵심 테스트 계획

> 최종 실행 결과는 [Core 대기열·동시성 검증 결과](./core-test-result.md)에 기록했습니다.

## 목적

테스트 범위를 대기열 도입 효과와 주문 동시성 정합성으로 제한합니다. 인증·결제·환불·프런트 전체 회귀 검증은 Core 완료 조건으로 사용하지 않습니다.

## 1. 빠른 회귀 테스트

```powershell
.\gradlew.bat test
```

검증 대상:

- 상품 판매 가능 조건
- 주문 요청 사전 조건과 fingerprint
- admission token claim·consume·release 조정
- queue snapshot, heartbeat와 stale member 정리

## 2. 실제 인프라 통합테스트

```powershell
.\gradlew.bat integrationTest
```

### PostgreSQL 동시성

- 재고 수량까지만 주문 성공
- 동일 checkout token의 단일 주문 생성
- 같은 token과 다른 payload의 충돌 거절
- 사용자별 활성 주문 하나만 유지
- 역순 다상품 주문의 deadlock 방지
- 사용자별 구매 한도 초과 방지

핵심 동시성 테스트는 10회 반복해 간헐 실패를 확인합니다.

```powershell
1..10 | ForEach-Object {
  .\gradlew.bat integrationTest `
    --tests '*CorePostgresConcurrencyIntegrationTest' `
    --rerun-tasks
  if ($LASTEXITCODE -ne 0) { throw "동시성 테스트 실패: $_" }
}
```

### Redis 대기열

- leave, heartbeat와 stale member 제거
- 동일 claim의 멱등 처리
- claim release와 consume
- 품절 시 queue 삭제와 generation 증가
- generation 변경 뒤 이전 claim 정리

## 3. k6 실험

1. smoke로 환경 확인
2. 핫 상품 주문으로 queue 전체 흐름 확인
3. 동일한 초기 상태에서 queue/direct 비교
4. 각 실행 직후 DB 정합성 SQL 확인

실행 방법은 [k6 가이드](../../k6/README.md)를 따릅니다.

## 완료 조건

- 단위·통합테스트 실패 0건
- 동시성 테스트 10회 반복 실패 0건
- 재고 음수 0건
- 동일 사용자·checkout token 중복 주문 0건
- 구매 한도 초과 0건
- queue/direct 비교 결과와 해석 기록
