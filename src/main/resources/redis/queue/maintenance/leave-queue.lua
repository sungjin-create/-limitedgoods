-- 사용자의 명시적인 대기열 이탈을 처리한다.
-- 주문이 PROCESSING 중이면 이탈을 거절하고, 그렇지 않으면 token/track과
-- waiting/activity 멤버를 하나의 원자적 작업으로 정리한다.
--
-- KEYS[1]: waiting ZSET
-- KEYS[2]: activity ZSET
-- KEYS[3]: 사용자별 admission track 키
--
-- ARGV[1]: userId
-- ARGV[2]: admission token Redis 키 prefix
--
-- 반환값:
-- 0: waiting에 존재하지 않음
-- 1: waiting에서 정상 제거
-- 2: admission token이 PROCESSING이라 이탈 불가

local trackedToken = redis.call('GET', KEYS[3])

if trackedToken then
    local tokenKey = ARGV[2] .. trackedToken
    local tokenValue = redis.call('GET', tokenKey)

    -- 주문이 사용 중인 토큰은 사용자가 임의로 삭제할 수 없다.
    if tokenValue and
        string.sub(tokenValue, 1, 11) == 'PROCESSING|'
    then
        return 2
    end

    redis.call('DEL', tokenKey)
    redis.call('DEL', KEYS[3])
end

local waitingRemoved = redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('ZREM', KEYS[2], ARGV[1])

return waitingRemoved
