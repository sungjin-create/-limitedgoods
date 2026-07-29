package com.limitedgoods.limitedgoods.backoffice.analytics.dto;

import com.limitedgoods.limitedgoods.analytics.entity.DailyOrderFunnelProjection;

import java.time.LocalDate;

public record DailyOrderFunnelResponse(
        LocalDate date,
        long createdOrderCount,
        long paidOrderCount,
        long paymentFailureAttemptCount,
        long expiredOrderCount,
        long refundedOrderCount,
        double paymentConversionRate,
        double expirationRate,
        double refundRate
) {

    public static DailyOrderFunnelResponse from(DailyOrderFunnelProjection projection) {
        return new DailyOrderFunnelResponse(
                projection.getOrderDate(),
                projection.getCreatedOrderCount(),
                projection.getPaidOrderCount(),
                projection.getPaymentFailureCount(),
                projection.getExpiredOrderCount(),
                projection.getRefundedOrderCount(),
                roundOneDecimal(projection.getPaymentConversionRate()),
                roundOneDecimal(projection.getExpirationRate()),
                roundOneDecimal(projection.getRefundRate())
        );
    }

    public static DailyOrderFunnelResponse empty(LocalDate date) {
        return new DailyOrderFunnelResponse(
                date,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}