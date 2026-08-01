-- waiting에 실제로 존재하는 사용자의 마지막 활동 시각만 갱신한다.
-- waiting 확인과 activity 갱신을 원자적으로 처리하여 이탈한 사용자의
-- activity 데이터가 다시 생성되는 것을 방지한다.
--
-- KEYS[1]: waiting ZSET
-- KEYS[2]: activity ZSET
--
-- ARGV[1]: userId
-- ARGV[2]: 현재 시각 milliseconds
--
-- 반환값:
-- 0: waiting에 존재하지 않음
-- 1: activity 갱신 완료

local score = redis.call('ZSCORE', KEYS[1], ARGV[1])

if not score then
    return 0
end

redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])

return 1
