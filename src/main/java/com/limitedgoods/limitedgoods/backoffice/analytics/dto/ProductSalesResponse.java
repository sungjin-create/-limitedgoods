package com.limitedgoods.limitedgoods.backoffice.analytics.dto;

import java.time.LocalDateTime;

public record ProductSalesResponse(
        Long productId,
        String productName,
        long paidOrderCount,
        long soldQuantity,
        long refundedQuantity,
        long netQuantity,
        long grossRevenue,
        long refundAmount,
        long netRevenue,
        LocalDateTime lastSoldAt
) {
}