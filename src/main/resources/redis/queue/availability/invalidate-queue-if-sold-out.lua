-- Cleanup Scheduler가 DB 재고 0을 발견했을 때 사용하는 방어적 정리 작업이다.
-- Lua 실행 시점에도 sold-out 키가 존재할 때만 대기열을 무효화한다.
-- 환불 커밋 후 sold-out 키가 먼저 삭제됐다면 복구된 새 대기열을 보호한다.
--
-- KEYS[1]: 상품 sold-out 키
-- KEYS[2]: 상품 대기열 generation 키
-- KEYS[3]: waiting ZSET
-- KEYS[4]: activity ZSET
--
-- 반환값:
-- 0: sold-out 키가 없어 아무 작업도 하지 않음
-- 1: generation 증가와 대기열 삭제 완료

if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

redis.call('INCR', KEYS[2])
redis.call('DEL', KEYS[3])
redis.call('DEL', KEYS[4])

return 1
