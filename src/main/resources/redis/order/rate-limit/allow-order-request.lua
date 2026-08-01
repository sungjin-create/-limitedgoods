-- 사용자와 상품 조합의 주문 요청 횟수를 제한한다.
-- INCR와 최초 만료 시간 설정을 원자적으로 실행하여 동시 요청에서도
-- 카운터만 생성되고 TTL이 누락되는 상황을 방지한다.
--
-- KEYS[1]: 사용자·상품별 rate-limit 카운터 키
--
-- ARGV[1]: 제한 시간 window seconds
-- ARGV[2]: window 안에서 허용할 최대 요청 수
--
-- 반환값:
-- 1: 요청 허용
-- 0: 최대 요청 횟수를 초과하여 거절

local count = redis.call('INCR', KEYS[1])

-- 카운터가 처음 생성된 요청에서만 window TTL을 설정한다.
-- 이후 요청은 기존 window의 종료 시각을 연장하지 않는다.
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end

if count > tonumber(ARGV[2]) then
    return 0
end

return 1