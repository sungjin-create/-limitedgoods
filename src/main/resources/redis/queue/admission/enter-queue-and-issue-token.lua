-- 사용자를 waiting/activity에 등록하고 active window 안에 들어온 경우
-- 기존 admission token을 재사용하거나 새 READY 토큰을 발급한다.
-- 품절 확인부터 대기열 등록과 토큰 발급까지 원자적으로 처리한다.
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
-- SOLD_OUT: 품절로 진입 거절
-- ERROR: waiting 등록 후에도 순위를 찾지 못한 비정상 상태
-- WAITING:{position}: 아직 active window 밖이며 position은 1부터 시작
-- ADMITTED:{token}: 입장 가능하며 기존 또는 신규 token UUID 반환
-- RETRY: 새 UUID 키가 충돌하여 Java에서 재시도 필요

if redis.call('EXISTS', KEYS[1]) == 1 then
    return 'SOLD_OUT'
end

local generation = redis.call('GET', KEYS[2]) or '0'

-- NX는 재진입 시 최초 score를 덮어쓰지 않아 기존 대기 순서를 보존한다.
redis.call('ZADD', KEYS[3], 'NX', ARGV[1], ARGV[2])

-- activity는 마지막 활동 시각이므로 진입 요청마다 현재 시각으로 갱신한다.
redis.call('ZADD', KEYS[6], ARGV[1], ARGV[2])

local rank = redis.call('ZRANK', KEYS[3], ARGV[2])

if not rank then
    return 'ERROR'
end

local activeWindow = tonumber(ARGV[5])

if rank >= activeWindow then
    local position = rank - activeWindow + 1
    return 'WAITING:' .. position
end

local readyValue = 'READY|' .. generation .. '|' .. ARGV[2] .. '|' .. ARGV[6]
local processingPrefix = 'PROCESSING|' .. generation .. '|' .. ARGV[2] .. '|' .. ARGV[6] .. '|'

-- track을 통해 이 사용자에게 전에 발급한 token UUID를 찾는다.
local existingToken = redis.call('GET', KEYS[4])

if existingToken then
    local existingTokenKey = ARGV[7] .. existingToken
    local existingValue = redis.call('GET', existingTokenKey)

    -- 같은 generation의 READY 토큰은 새로 발급하지 않고 재사용한다.
    if existingValue == readyValue then
        return 'ADMITTED:' .. existingToken
    end

    -- 같은 generation에서 주문 처리 중인 토큰도 중복 발급하지 않는다.
    if existingValue and
        string.sub(existingValue, 1, string.len(processingPrefix)) == processingPrefix
    then
        return 'ADMITTED:' .. existingToken
    end

    -- token 만료나 generation 변경으로 오래된 track/token을 정리한다.
    redis.call('DEL', existingTokenKey)
    redis.call('DEL', KEYS[4])
end

-- NX는 극히 드문 UUID 충돌에서도 기존 token 키를 덮어쓰지 않게 한다.
local created = redis.call('SET', KEYS[5], readyValue, 'PX', ARGV[4], 'NX')

if not created then
    return 'RETRY'
end

-- 사용자별 track과 실제 token 키의 TTL을 동일하게 맞춘다.
redis.call('SET', KEYS[4], ARGV[3], 'PX', ARGV[4])

return 'ADMITTED:' .. ARGV[3]
