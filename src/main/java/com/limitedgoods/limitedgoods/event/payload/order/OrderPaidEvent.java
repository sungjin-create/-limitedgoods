package com.limitedgoods.limitedgoods.event.payload.order;

import java.time.LocalDateTime;
import java.util.List;

public record OrderPaidEvent(
        Long orderId,
        Long userId,
        String recipientEmail,
        long totalPrice,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        List<OrderPaidItem> items
) {
}
