-- Cleanup Scheduler가 찾은 비활성 후보를 실제로 제거한다.
-- 후보 조회 후 heartbeat가 들어올 수 있으므로 Lua 실행 시점의 activity 점수를
-- 다시 확인하여 정상 사용자를 잘못 삭제하는 경쟁 조건을 방지한다.
--
-- KEYS[1]: waiting ZSET
-- KEYS[2]: activity ZSET
-- KEYS[3]: 사용자별 admission track 키
--
-- ARGV[1]: userId
-- ARGV[2]: 비활성 판정 기준 시각(cutoff) milliseconds
-- ARGV[3]: admission token Redis 키 prefix
--
-- 반환값:
-- 0: 최근 활동이 있어 제거하지 않음
-- 1: stale 사용자 정리 완료
-- 2: admission token이 PROCESSING이라 정리하지 않음

local lastActivity = redis.call('ZSCORE', KEYS[2], ARGV[1])

-- activity가 유실됐지만 waiting에는 남은 불완전 상태를 정리한다.
if not lastActivity then
    redis.call('ZREM', KEYS[1], ARGV[1])
    return 1
end

-- 후보를 조회한 뒤 heartbeat가 갱신했다면 제거하지 않는다.
if tonumber(lastActivity) > tonumber(ARGV[2]) then
    return 0
end

local trackedToken = redis.call('GET', KEYS[3])

if trackedToken then
    local tokenKey = ARGV[3] .. trackedToken
    local tokenValue = redis.call('GET', tokenKey)

    -- 주문 처리 중인 토큰은 stale cleanup에서도 보호한다.
    if tokenValue and
        string.sub(tokenValue, 1, 11) == 'PROCESSING|'
    then
        return 2
    end

    redis.call('DEL', tokenKey)
    redis.call('DEL', KEYS[3])
end

redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('ZREM', KEYS[2], ARGV[1])

return 1
