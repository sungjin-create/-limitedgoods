package com.limitedgoods.limitedgoods.payment.dto;

import java.time.LocalDateTime;

public record RefundResult(
        String refundId,
        long refundedAmount,
        LocalDateTime refundedAt
) {
}