-- 이미 waiting에 존재하는 사용자의 상태를 조회한다.
-- active window에 진입했다면 기존 토큰을 재사용하거나 새 READY 토큰을 발급한다.
-- enter 스크립트와 달리 waiting에 없는 사용자를 새로 등록하지 않는다.
--
-- KEYS[1]: 상품 sold-out 키
-- KEYS[2]: 상품 대기열 generation 키
-- KEYS[3]: waiting ZSET. score=최초 진입 시각, member=userId
-- KEYS[4]: 사용자별 admission track 키. value=token UUID
-- KEYS[5]: 이번 요청에서 생성할 admission token 키
-- KEYS[6]: activity ZSET. score=마지막 활동 시각, member=userId
--
-- ARGV[1]: 현재 시각 milliseconds
-- ARGV[2]: userId
-- ARGV[3]: 새 admission token UUID
-- ARGV[4]: token TTL milliseconds
-- ARGV[5]: 즉시 입장 가능한 active window 크기
-- ARGV[6]: productId
-- ARGV[7]: admission token Redis 키 prefix
--
-- 반환값:
-- SOLD_OUT, NOT_FOUND, WAITING:{position}, ADMITTED:{token}, RETRY

if redis.call('EXISTS', KEYS[1]) == 1 then
    return 'SOLD_OUT'
end

local generation = redis.call('GET', KEYS[2]) or '0'

-- 상태 조회에서는 기존 순위만 찾고 waiting에 새 사용자를 추가하지 않는다.
local rank = redis.call('ZRANK', KEYS[3], ARGV[2])

if not rank then
    return 'NOT_FOUND'
end

-- 정상적인 상태 조회를 heartbeat처럼 취급한다.
redis.call('ZADD', KEYS[6], ARGV[1], ARGV[2])

local activeWindow = tonumber(ARGV[5])

if rank >= activeWindow then
    local position = rank - activeWindow + 1
    return 'WAITING:' .. position
end

local readyValue = 'READY|' .. generation .. '|' .. ARGV[2] .. '|' .. ARGV[6]
local processingPrefix = 'PROCESSING|' .. generation .. '|' .. ARGV[2] .. '|' .. ARGV[6] .. '|'
local existingToken = redis.call('GET', KEYS[4])

if existingToken then
    local existingTokenKey = ARGV[7] .. existingToken
    local existingValue = redis.call('GET', existingTokenKey)

    if existingValue == readyValue then
        return 'ADMITTED:' .. existingToken
    end

    if existingValue and
        string.sub(existingValue, 1, string.len(processingPrefix)) == processingPrefix
    then
        return 'ADMITTED:' .. existingToken
    end

    -- 만료됐거나 이전 generation에 속한 token과 track을 정리한다.
    redis.call('DEL', existingTokenKey)
    redis.call('DEL', KEYS[4])
end

local tokenCreated = redis.call('SET', KEYS[5], readyValue, 'PX', ARGV[4], 'NX')

if not tokenCreated then
    return 'RETRY'
end

redis.call('SET', KEYS[4], ARGV[3], 'PX', ARGV[4])

return 'ADMITTED:' .. ARGV[3]
