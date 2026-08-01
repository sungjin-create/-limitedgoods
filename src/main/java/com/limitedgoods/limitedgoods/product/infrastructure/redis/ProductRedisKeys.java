package com.limitedgoods.limitedgoods.product.infrastructure.redis;

import com.limitedgoods.limitedgoods.common.infrastructure.redis.RedisKeyNamespace;

public final class ProductRedisKeys {

    private static final String PREFIX =
            RedisKeyNamespace.ROOT + ":product";

    private ProductRedisKeys() {
    }

    public static String soldOut(Long productId) {
        return PREFIX + ":{" + productId + "}:sold-out";
    }

    public static String queueState(Long productId) {
        return PREFIX + ":{" + productId + "}:queue-state";
    }
}