-- READY admission token을 특정 주문 요청이 독점하도록 PROCESSING으로 전환한다.
-- 이 스크립트 전체는 Redis에서 원자적으로 실행되므로 두 주문이 같은 토큰을
-- 동시에 선점하는 상황을 방지한다.
--
-- KEYS[1]: admission token 키
--          예) limitedgoods:v1:queue:{10}:admission:token:{uuid}
-- KEYS[2]: 상품 대기열 generation 키
--
-- ARGV[1]: userId
-- ARGV[2]: productId
-- ARGV[3]: claimId. 동일 주문 재요청을 식별하는 값
--
-- 반환값:
-- generation: 선점 성공 또는 같은 claimId의 멱등 재요청
-- nil: 유효하지 않거나 다른 요청이 이미 선점한 토큰

-- generation 키가 아직 없다면 최초 세대인 0으로 취급한다.
local generation = redis.call('GET', KEYS[2]) or '0'

-- 현재 사용자와 상품이 가진 정상적인 미사용 토큰의 값이다.
local readyValue = 'READY|' .. generation .. '|' .. ARGV[1] .. '|' .. ARGV[2]

-- 현재 주문이 토큰을 선점했을 때 저장할 값이다.
local processingValue = 'PROCESSING|' .. generation .. '|' .. ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3]

local tokenValue = redis.call('GET', KEYS[1])

-- 같은 claimId가 이미 선점했다면 멱등 재요청이므로 성공으로 처리한다.
if tokenValue == processingValue then
    return generation
end

-- 토큰이 없거나 READY가 아니거나 사용자·상품·generation이 다르면 거절한다.
if tokenValue ~= readyValue then
    return nil
end

-- 상태를 바꿀 때 기존 토큰의 남은 만료 시간을 그대로 유지한다.
local ttl = redis.call('PTTL', KEYS[1])

-- 이미 만료됐거나 TTL이 없는 비정상 토큰은 선점하지 않는다.
if ttl <= 0 then
    return nil
end

redis.call('SET', KEYS[1], processingValue, 'PX', ttl)

-- complete/release가 claim 당시 세대를 사용할 수 있도록 반환한다.
return generation
