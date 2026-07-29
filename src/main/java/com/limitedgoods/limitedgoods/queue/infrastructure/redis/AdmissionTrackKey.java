package com.limitedgoods.limitedgoods.queue.infrastructure.redis;

public record AdmissionTrackKey(
        Long productId,
        Long userId
) {
}