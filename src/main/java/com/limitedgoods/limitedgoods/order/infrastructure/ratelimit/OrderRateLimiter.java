package com.limitedgoods.limitedgoods.order.infrastructure.ratelimit;

import com.limitedgoods.limitedgoods.order.infrastructure.redis.OrderRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRateLimiter {

    private static final int MAX_REQUESTS = 3;
    private static final long WINDOW_SECONDS = 10;

    private static final RedisScript<Long> ALLOW_ORDER_REQUEST_SCRIPT =
            RedisScript.of(
                    new ClassPathResource("redis/order/rate-limit/allow-order-request.lua"),
                    Long.class
            );

    private final RedisTemplate<String, String> redisTemplate;

    public boolean allow(Long userId, Long productId) {
        String key = OrderRedisKeys.rateLimit(userId, productId);

        try {
            Long result = redisTemplate.execute(
                    ALLOW_ORDER_REQUEST_SCRIPT,
                    List.of(key),
                    String.valueOf(WINDOW_SECONDS),
                    String.valueOf(MAX_REQUESTS)
            );

            return Long.valueOf(1L).equals(result);

        } catch (Exception e) {
            log.warn(
                    "주문 요청 제한 확인 실패. 요청을 통과시킵니다. userId={}, productId={}",
                    userId,
                    productId,
                    e
            );

            return true;
        }
    }
}