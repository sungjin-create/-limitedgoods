package com.limitedgoods.limitedgoods.event.payload.order;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCanceledEvent(
        Long orderId,
        Long userId,
        long refundAmount,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        @JsonAlias("canceledAt")
        LocalDateTime refundedAt,
        List<OrderCanceledItem> items
) {
}