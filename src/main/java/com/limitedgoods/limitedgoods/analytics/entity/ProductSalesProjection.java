package com.limitedgoods.limitedgoods.analytics.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "product_sales_projection",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_product_sales_projection_product",
                columnNames = "product_id"
        )
)
public class ProductSalesProjection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "paid_order_count", nullable = false)
    private long paidOrderCount;

    @Column(name = "sold_quantity", nullable = false)
    private long soldQuantity;

    @Column(name = "gross_revenue", nullable = false)
    private long grossRevenue;

    @Column(name = "refunded_quantity", nullable = false)
    private long refundedQuantity;

    @Column(name = "refund_amount", nullable = false)
    private long refundAmount;

    @Column(name = "last_sold_at")
    private LocalDateTime lastSoldAt;

    public long getNetQuantity() {
        return soldQuantity - refundedQuantity;
    }

    public long getNetRevenue() {
        return grossRevenue - refundAmount;
    }
}