package com.limitedgoods.limitedgoods.backoffice.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsOverviewResponse(
        LocalDate from,
        LocalDate to,
        SalesSummary salesSummary,
        FunnelSummary funnelSummary,
        List<DailySalesResponse> dailySales,
        List<DailyOrderFunnelResponse> dailyOrderFunnel
) {

    public record SalesSummary(
            long paidOrderCount,
            long grossRevenue,
            long refundedOrderCount,
            long refundAmount,
            long netRevenue,
            long soldQuantity,
            long refundedQuantity,
            long netQuantity,
            double averageOrderAmount
    ) {
    }

    public record FunnelSummary(
            long createdOrderCount,
            long paidOrderCount,
            long paymentFailureAttemptCount,
            long expiredOrderCount,
            long canceledOrderCount,
            double paymentConversionRate,
            double expirationRate,
            double refundRate
    ) {
    }
}