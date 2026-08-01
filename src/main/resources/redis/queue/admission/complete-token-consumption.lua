-- 주문 생성이 성공한 뒤 해당 주문이 선점한 admission token을 소비 완료한다.
-- 현재 상품 generation이 아니라 claim 당시 generation으로 PROCESSING 값을 검증한다.
-- 마지막 재고 주문이 generation을 증가시킨 뒤에도 자신의 토큰을 정리해야 하기 때문이다.
--
-- KEYS[1]: admission token 키
-- KEYS[2]: 사용자별 admission track 키
--
-- ARGV[1]: claim 당시 generation
-- ARGV[2]: userId
-- ARGV[3]: productId
-- ARGV[4]: claimId
-- ARGV[5]: admission token UUID
--
-- 반환값:
-- 1: 토큰 소비 완료
-- 0: 현재 요청이 선점한 토큰이 아니어서 아무 작업도 하지 않음

local processingValue = 'PROCESSING|' .. ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3] .. '|' .. ARGV[4]
local tokenValue = redis.call('GET', KEYS[1])

-- 다른 주문이 선점했거나 이미 정리된 토큰은 삭제하지 않는다.
if tokenValue ~= processingValue then
    return 0
end

redis.call('DEL', KEYS[1])

-- track이 방금 소비한 UUID를 가리킬 때만 삭제한다.
-- 혹시 더 최신 토큰을 가리키고 있다면 그 track은 보존한다.
local trackedToken = redis.call('GET', KEYS[2])

if trackedToken == ARGV[5] then
    redis.call('DEL', KEYS[2])
end

return 1
