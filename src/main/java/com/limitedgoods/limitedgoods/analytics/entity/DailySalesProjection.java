package com.limitedgoods.limitedgoods.analytics.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "daily_sales_projection",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_daily_sales_projection_date",
                columnNames = "sales_date"
        )
)
public class DailySalesProjection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_date", nullable = false)
    private LocalDate salesDate;

    @Column(name = "paid_order_count", nullable = false)
    private long paidOrderCount;

    @Column(name = "gross_revenue", nullable = false)
    private long grossRevenue;

    @Column(name = "refunded_order_count", nullable = false)
    private long refundedOrderCount;

    @Column(name = "refund_amount", nullable = false)
    private long refundAmount;

    @Column(name = "sold_quantity", nullable = false)
    private long soldQuantity;

    public long getNetRevenue() {
        return grossRevenue - refundAmount;
    }

    public double getAverageOrderAmount() {
        if (paidOrderCount == 0) {
            return 0;
        }

        return (double) grossRevenue / paidOrderCount;
    }
}