-- 판매 종료, 상품 삭제, 판매 중지 등 품절 여부와 무관한 사유로
-- 상품 대기열 전체를 무효화한다.
--
-- KEYS[1]: 상품 대기열 generation 키
-- KEYS[2]: waiting ZSET
-- KEYS[3]: activity ZSET
--
-- 반환값:
-- 증가한 새 generation

local generation = redis.call('INCR', KEYS[1])

redis.call('DEL', KEYS[2])
redis.call('DEL', KEYS[3])

return generation
