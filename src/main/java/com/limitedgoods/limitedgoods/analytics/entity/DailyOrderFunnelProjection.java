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
        name = "daily_order_funnel_projection",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_daily_order_funnel_date",
                columnNames = "order_date"
        )
)
public class DailyOrderFunnelProjection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "created_order_count", nullable = false)
    private long createdOrderCount;

    @Column(name = "paid_order_count", nullable = false)
    private long paidOrderCount;

    @Column(name = "payment_failure_count", nullable = false)
    private long paymentFailureCount;

    @Column(name = "expired_order_count", nullable = false)
    private long expiredOrderCount;

    @Column(name = "canceled_order_count", nullable = false)
    private long canceledOrderCount;

    public double getPaymentConversionRate() {
        if (createdOrderCount == 0) {
            return 0;
        }

        return (double) paidOrderCount
                / createdOrderCount
                * 100;
    }

    public double getExpirationRate() {
        if (createdOrderCount == 0) {
            return 0;
        }

        return (double) expiredOrderCount
                / createdOrderCount
                * 100;
    }

    public double getRefundRate() {
        if (paidOrderCount == 0) {
            return 0;
        }

        return (double) canceledOrderCount
                / paidOrderCount
                * 100;
    }
}