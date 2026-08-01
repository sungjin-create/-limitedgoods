-- 명확한 주문 실패 후 PROCESSING 토큰의 선점을 해제한다.
-- generation이 유지됐다면 READY로 복구하고, generation이 바뀌었다면
-- 이전 판매 구간의 토큰이므로 READY로 복구하지 않고 삭제한다.
--
-- KEYS[1]: admission token 키
-- KEYS[2]: 사용자별 admission track 키
-- KEYS[3]: 상품 대기열의 현재 generation 키
--
-- ARGV[1]: claim 당시 generation
-- ARGV[2]: userId
-- ARGV[3]: productId
-- ARGV[4]: claimId
-- ARGV[5]: admission token UUID
--
-- 반환값:
-- 0: 현재 요청이 선점한 토큰이 아니거나 복구할 수 없음
-- 1: 같은 generation에서 PROCESSING을 READY로 복구
-- 2: generation이 바뀌어 이전 token/track을 삭제

local processingValue = 'PROCESSING|' .. ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3] .. '|' .. ARGV[4]
local tokenValue = redis.call('GET', KEYS[1])

if tokenValue ~= processingValue then
    return 0
end

local currentGeneration = redis.call('GET', KEYS[3]) or '0'
local trackedToken = redis.call('GET', KEYS[2])

if currentGeneration ~= ARGV[1] then
    redis.call('DEL', KEYS[1])

    -- track이 삭제 대상 UUID를 가리킬 때만 제거해 최신 track을 보호한다.
    if trackedToken == ARGV[5] then
        redis.call('DEL', KEYS[2])
    end

    return 2
end

-- 같은 generation에서는 기존 만료 시간을 유지한 채 READY로 복구한다.
local ttl = redis.call('PTTL', KEYS[1])

if ttl <= 0 then
    return 0
end

local readyValue = 'READY|' .. ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3]
redis.call('SET', KEYS[1], readyValue, 'PX', ttl)

return 1
