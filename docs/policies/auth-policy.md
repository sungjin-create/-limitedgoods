# 인증 정책

## Token 구성

- Access Token: 기본 15분 JWT, 프런트 메모리에 저장
- Refresh Token: 기본 7일 무작위 token, HttpOnly cookie로 전달
- 서버 저장값: Refresh Token 원문이 아닌 SHA-256 hash
- cookie 이름과 경로: `refresh_token`, `/api/user`
- SameSite: `Lax`
- Secure: local `false`, prod `true`

Access Token에는 사용자 ID, 이메일, 역할과 `tokenVersion`이 포함됩니다. 보호 API 요청 시 서명과 만료뿐 아니라 현재 사용자 상태, 역할, `tokenVersion`도 DB와 대조합니다.

## 로그인과 회전

로그인 성공 시 Access Token과 Refresh Token을 함께 발급합니다. `/api/user/refresh`는 cookie의 기존 Refresh Token row를 비관적 잠금으로 조회한 뒤 다음 조건을 검사합니다.

- 폐기되지 않음
- 만료 시각 전
- 사용자가 `ACTIVE`
- 발급 당시 `tokenVersion`과 현재 값이 같음

검증에 성공하면 기존 token을 폐기하고 새 Refresh Token과 Access Token을 발급합니다. 같은 Refresh Token의 동시 재사용은 한 요청만 성공해야 합니다.

## 로그아웃과 무효화

`/api/user/logout`은 전달된 Refresh Token을 폐기하고 cookie의 `Max-Age`를 0으로 설정합니다. cookie가 없거나 이미 폐기된 로그아웃은 성공으로 처리합니다.

사용자 정지·활성화 또는 역할 변경으로 `tokenVersion`이 증가하면 그 전에 발급된 Access Token과 Refresh Token은 모두 사용할 수 없습니다.

## 브라우저 정책

프런트는 모든 API 요청에 `credentials: include`를 사용합니다. Access Token이 만료되어 보호 API가 `401`을 반환하면 refresh 요청은 동시에 하나만 실행하고, 성공한 경우 원래 요청을 한 번만 재시도합니다. refresh도 실패하면 메모리 token을 제거하고 로그아웃 상태로 전환합니다.
