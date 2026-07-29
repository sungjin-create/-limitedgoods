package com.limitedgoods.limitedgoods.order.application.cancel.dto;

import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;

public record RefundStartResult(
        RefundStartAction action,
        Long refundAttemptId,
        Long userId,
        Long orderId,
        String pgTransactionId,
        long amount,
        String idempotencyKey,
        OrderResponse completedOrder
) {
}