package com.limitedgoods.limitedgoods.backoffice.analytics.dto;

import com.limitedgoods.limitedgoods.analytics.entity.DailySalesProjection;

import java.time.LocalDate;

public record DailySalesResponse(
        LocalDate date,
        long paidOrderCount,
        long grossRevenue,
        long refundedOrderCount,
        long refundAmount,
        long netRevenue,
        long soldQuantity,
        double averageOrderAmount
) {

    public static DailySalesResponse from(DailySalesProjection projection) {
        return new DailySalesResponse(
                projection.getSalesDate(),
                projection.getPaidOrderCount(),
                projection.getGrossRevenue(),
                projection.getRefundedOrderCount(),
                projection.getRefundAmount(),
                projection.getNetRevenue(),
                projection.getSoldQuantity(),
                roundOneDecimal(
                        projection.getAverageOrderAmount()
                )
        );
    }

    public static DailySalesResponse empty(LocalDate date) {
        return new DailySalesResponse(
                date,
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