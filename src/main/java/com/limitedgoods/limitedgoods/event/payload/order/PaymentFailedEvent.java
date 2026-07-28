package com.limitedgoods.limitedgoods.event.payload.order;

import java.time.LocalDateTime;

public record PaymentFailedEvent (
        Long orderId,
        Long userId,
        LocalDateTime createdAt,
        LocalDateTime failedAt,
        String failureReason
){
}
