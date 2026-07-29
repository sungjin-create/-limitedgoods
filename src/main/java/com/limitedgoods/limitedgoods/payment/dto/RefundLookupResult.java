package com.limitedgoods.limitedgoods.payment.dto;

import java.time.LocalDateTime;

public record RefundLookupResult(
        RefundLookupStatus status,
        String refundId,
        long refundedAmount,
        LocalDateTime refundedAt,
        String failureCode,
        String failureReason
) {
    public RefundResult toRefundResult() {
        return new RefundResult(
                refundId,
                refundedAmount,
                refundedAt
        );
    }
}