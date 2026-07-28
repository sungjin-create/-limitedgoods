package com.limitedgoods.limitedgoods.event.payload.order;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCanceledEvent(
        Long orderId,
        Long userId,
        long refundAmount,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime canceledAt,
        List<OrderCanceledItem> items
) {
}