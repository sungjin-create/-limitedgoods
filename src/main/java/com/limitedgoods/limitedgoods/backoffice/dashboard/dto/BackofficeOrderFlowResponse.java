package com.limitedgoods.limitedgoods.backoffice.dashboard.dto;

import lombok.Builder;


@Builder
public record BackofficeOrderFlowResponse (
    long createdCount,
    long paidCount,
    long pendingCount,

    long paymentFailedCount,
    long expiredCount,

    long refundRequestedCount,
    long refundedCount,
    long refundFailedCount
) {
}
