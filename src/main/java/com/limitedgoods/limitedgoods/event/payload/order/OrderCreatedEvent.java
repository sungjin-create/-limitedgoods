package com.limitedgoods.limitedgoods.event.payload.order;

import java.time.LocalDateTime;

public record OrderCreatedEvent (
        Long orderId,
        Long userId,
        long totalPrice,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
){
}
