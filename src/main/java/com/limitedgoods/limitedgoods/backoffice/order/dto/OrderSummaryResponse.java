package com.limitedgoods.limitedgoods.backoffice.order.dto;

public record OrderSummaryResponse(
        long paymentPendingCount,
        long paidCount,
        long cancelRequestedCount,
        long refundedCount,
        long paymentFailedCount,
        long expiredCount,
        long cancelFailedCount
) {
}