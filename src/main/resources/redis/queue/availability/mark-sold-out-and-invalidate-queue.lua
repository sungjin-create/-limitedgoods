-- 상품을 품절로 표시하고 현재 대기열 세대를 무효화한다.
-- sold-out 등록, generation 증가, waiting/activity 삭제가 원자적으로 실행되므로
-- 진입 요청이 품절 처리 중간에 끼어드는 것을 방지한다.
--
-- KEYS[1]: 상품 sold-out 키
-- KEYS[2]: 상품 대기열 generation 키
-- KEYS[3]: waiting ZSET
-- KEYS[4]: activity ZSET
--
-- ARGV[1]: sold-out 키 TTL milliseconds
--
-- 반환값:
-- 증가한 새 generation

redis.call('SET', KEYS[1], 'true', 'PX', ARGV[1])

-- 기존 READY/PROCESSING 토큰은 새 generation과 일치하지 않아 논리적으로 무효화된다.
local generation = redis.call('INCR', KEYS[2])

redis.call('DEL', KEYS[3])
redis.call('DEL', KEYS[4])

return generation
