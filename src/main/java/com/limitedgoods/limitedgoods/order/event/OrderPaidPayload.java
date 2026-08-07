package com.limitedgoods.limitedgoods.order.event;

import java.time.LocalDateTime;

public record OrderPaidPayload(
        Long orderId,
        Long userId,
        long totalPrice,
        LocalDateTime paidAt
) {
}
