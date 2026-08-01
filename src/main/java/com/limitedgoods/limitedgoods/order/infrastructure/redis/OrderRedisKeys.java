package com.limitedgoods.limitedgoods.order.infrastructure.redis;

import com.limitedgoods.limitedgoods.common.infrastructure.redis.RedisKeyNamespace;

public final class OrderRedisKeys {

    private static final String PREFIX = RedisKeyNamespace.ROOT + ":order";

    private OrderRedisKeys() {
    }

    public static String rateLimit(Long userId, Long productId) {
        return PREFIX + ":rate-limit:" + userId + ":" + productId;
    }
}