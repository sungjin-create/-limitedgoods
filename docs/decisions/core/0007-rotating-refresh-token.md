# ADR-0007: 회전형 Refresh Token을 hash로 저장

- 상태: accepted

## 문제

Access Token을 짧게 유지하면 탈취 피해 시간은 줄지만 사용자는 만료될 때마다 다시 로그인해야 합니다. 반대로 장기 JWT 하나만 사용하면 즉시 폐기와 재사용 탐지가 어렵습니다.

## 결정

Access Token은 15분 JWT로 유지하고 Refresh Token은 7일 무작위 값으로 발급합니다. 원문은 HttpOnly cookie에만 전달하고 PostgreSQL에는 SHA-256 hash, 사용자, 발급 당시 `tokenVersion`, 만료·폐기 시각을 저장합니다.

refresh 시 기존 row를 잠그고 폐기한 뒤 새 token을 발급합니다. 로그아웃은 token을 폐기하고 cookie를 만료시킵니다.

## 결과

페이지 새로고침과 Access Token 만료 후 세션을 복원하면서도 사용자 정지·역할 변경·로그아웃을 서버에서 반영할 수 있습니다. 요청마다 사용자 상태를 확인하는 DB 비용과 만료·폐기 token 정리 정책은 후속 운영 과제로 남습니다.
